package pt.tomba.roteiro2arc;

//ArcConverter - Traverse the directory and writes to ARC files the files that have a URL in the DB
// (previously captured in the URL extraction step). During the file writing process, local paths inside
// the file are converted to URLs (if the correlation of local-path/URL is already know or generated
// if it didn't exist).
// The last step is to export to ARC files all the files that have a generatted URL (using the base URL
// of the file that refered them) so that no link file or resource is lost.
//Modified by: David Cruz <david.cruz@fccn.pt>
//For: SAW Group - FCCN <sawfccn@fccn.pt>
//
//Original version from: SiteCapturer.java - HTMLParser Library $Name: v1_6 $ - A java-based parser for HTML
//http://sourceforge.org/projects/htmlparser
//Copyright (C) 2003 Derrick Oswald
//
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
//
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//Lesser General Public License for more details.
//
//You should have received a copy of the GNU Lesser General Public
//License along with this library; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ArrayIndexOutOfBoundsException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.archive.io.arc.ARCConstants;
import org.archive.io.arc.ARCWriter;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.PrototypicalNodeFactory;
import org.htmlparser.tags.BaseHrefTag;
import org.htmlparser.tags.FrameTag;
import org.htmlparser.tags.ImageTag;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.EncodingChangeException;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.semanticdesktop.aperture.mime.identifier.MimeTypeIdentifier;
import org.semanticdesktop.aperture.mime.identifier.magic.MagicMimeTypeIdentifierFactory;

import pt.tomba.roteiro2arc.db.DatabaseEnvironment;
import pt.tomba.roteiro2arc.db.PathTranslationDataAccessor;
import pt.tomba.roteiro2arc.model.PathTranslation;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityCursor;

/**
 * Save a web site locally.
 * Illustrative program to save a web site contents locally.
 * It was created to demonstrate URL rewriting in it's simplest form.
 * It uses customized tags in the NodeFactory to alter the URLs.
 * This program has a number of limitations:
 * <ul>
 * <li>it doesn't capture forms, this would involve too many assumptions</li>
 * <li>it doesn't capture script references, so funky onMouseOver and other
 * non-static content will not be faithfully reproduced</li>
 * <li>it doesn't handle style sheets</li>
 * <li>it doesn't dig into attributes that might reference resources, so
 * for example, background images won't necessarily be captured</li>
 * <li>worst of all, it gets confused when a URL both has content and is
 * the prefix for other content,
 * i.e. http://whatever.com/top and http://whatever.com/top/sub.html both
 * yield content, since this cannot be faithfully replicated to a static
 * directory structure (this happens a lot with servlet based sites)</li>
 *</ul>
 */
public class ArcConverter
{

	//TODO - relative links can have anchor. Problems!

	public final static String BASE_NAME = "BASENAME";

	public final static String PREFIX = "PREFIX";

	public final static String DEFAULT_IP = "1.1.1.1"; //TODO - confirm the value of the IP to use

	/**
	 * The web site to capture.
	 * This is used as the base URL in deciding whether to adjust a link
	 * and whether to capture a page or not.
	 */
	protected String mSource;

	/**
	 * The local directory to capture to.
	 * This is used as a base prefix for files saved locally.
	 */
	protected String mTarget;

	/**
	 * The index that contains the information to convert from local links to url
	 */
	//protected ForwardIndex linkIndex;

	/**
	 * The parser to use for processing.
	 */
	protected Parser mParser;

	/**
	 * The filter to apply to the nodes retrieved.
	 */
	protected NodeFilter mFilter;

	/**
	 * 
	 */
	protected ARCWriter writer; 

	/**
	 * 
	 */
	protected static Pattern pattern;

	/**
	 * 
	 */
	protected MimeTypeIdentifier identifier;

	/**
	 * 
	 */
	protected DatabaseEnvironment env;

	/**
	 * 
	 */
	protected DatabaseEnvironment recoveryEnv;

	/**
	 * 
	 */
	protected PathTranslationDataAccessor accessor;

	/**
	 * 
	 */
	protected PathTranslationDataAccessor recoveryAccessor;

	/**
	 * 
	 */
	protected String currentUrl;

	/**
	 * Create a web site capturer.
	 */
	public ArcConverter ()
	{
		PrototypicalNodeFactory factory;

		mSource = null;
		mTarget = null;
		mParser = new Parser ();
		factory = new PrototypicalNodeFactory ();
		factory.registerTag (new LocalLinkTag ());
		//TODO - remove: factory.registerTag (new LocalFrameTag ());
		factory.registerTag (new LocalBaseHrefTag ());
		factory.registerTag (new LocalImageTag ());
		mParser.setNodeFactory (factory);
		mFilter = null;
		pattern = Pattern.compile("http://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}).*");
		identifier = new MagicMimeTypeIdentifierFactory().get();
	}

	/**
	 * Getter for property source.
	 * @return Value of property source.
	 */
	public String getSource ()
	{
		return (mSource);
	}

	/**
	 * Setter for property source.
	 * This is the base URL to capture. URL's that don't start with this prefix
	 * are ignored (left as is), while the ones with this URL as a base are
	 * re-homed to the local target.
	 * @param source New value of property source.
	 */
	public void setSource (String source)
	{
		if (source.endsWith ("/"))
			source = source.substring (0, source.length () - 1);

		mSource = source;
	}

	/**
	 * Getter for property target.
	 * @return Value of property target.
	 */
	public String getTarget ()
	{
		return (mTarget);
	}

	/**
	 * Setter for property target.
	 * This is the local directory under which to save the site's pages.
	 * @param target New value of property target.
	 */
	public void setTarget (String target)
	{
		mTarget = target;
	}

	/**
	 * 
	 * @return
	 */
	public PathTranslationDataAccessor getDatabase ()
	{
		return accessor;
	}

	/**
	 * 
	 * @param location
	 */
	public void setDatabase (PathTranslationDataAccessor database)
	{
		accessor = database;
	}

	/**
	 * 
	 * @return
	 */
	public PathTranslationDataAccessor getRecoveryDatabase ()
	{
		return recoveryAccessor;
	}

	/**
	 * 
	 * @param location
	 */
	public void setRecoveryDatabase (PathTranslationDataAccessor database)
	{
		recoveryAccessor = database;
	}

	/** Getter for property filter.
	 * @return Value of property filter.
	 *
	 */
	public NodeFilter getFilter ()
	{
		return (mFilter);
	}

	/** Setter for property filter.
	 * @param filter New value of property filter.
	 *
	 */
	public void setFilter (NodeFilter filter)
	{
		mFilter = filter;
	}

	/**
	 * 
	 * @return
	 */
	public ARCWriter getARCWriter() {
		return writer;
	}

	/**
	 * 
	 * @param writer
	 */
	public void setARCWriter(ARCWriter writer) {
		this.writer = writer;
	}

	public String getCurrentUrl() {
		return currentUrl;
	}

	public void setCurrentUrl(String url) {
		currentUrl = url;
	}


	/**
	 * Converts a link to local.
	 * A relative link can be used to construct both a URL and a file name.
	 * Basically, the operation is to strip off the base url, if any,
	 * and then prepend as many dot-dots as necessary to make
	 * it relative to the current page.
	 * A bit of a kludge handles the root page specially by calling it
	 * index.html, even though that probably isn't it's real file name.
	 * This isn't pretty, but it works for me.
	 * @param link The link to make relative.
	 * @param current The current page URL, or empty if it's an absolute URL
	 * that needs to be converted.
	 * @return The URL relative to the current page.
	 */
	protected String makeLocalLink (String link, String current)
	{
		int i;
		int j;
		String ret;

		if (link.equals (getSource ()) || (!getSource ().endsWith ("/") && link.equals (getSource () + "/")))
			ret = "index.html"; // handle the root page specially
		else if (link.startsWith (getSource ())
				&& (link.length () > getSource ().length ()))
			ret = link.substring (getSource ().length () + 1);
		else
			ret = link; // give up

		// make it relative to the current page by prepending "../" for
		// each '/' in the current local path
		if ((null != current)
				&& link.startsWith (getSource ())
				&& (current.length () > getSource ().length ()))
		{
			current = current.substring (getSource ().length () + 1);
			i = 0;
			while (-1 != (j = current.indexOf ('/', i)))
			{
				ret = "../" + ret;
				i = j + 1;
			}
		}

		return (ret);
	}

	/**
	 * 
	 * @param date_string
	 * @return
	 * @throws ParseException
	 */
	public static Date normalize(String date_string) throws ParseException {
		SimpleDateFormat from = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

		return from.parse(date_string);
	}

	/**
	 * Unescape a URL to form a file name.
	 * Very crude.
	 * @param raw The escaped URI.
	 * @return The native URI.
	 */
	protected String decode (String raw)
	{
		int length;
		int start;
		int index;
		int value;
		StringBuffer ret;

		ret = new StringBuffer (raw.length ());

		length = raw.length ();
		start = 0;
		while (-1 != (index = raw.indexOf ('%', start)))
		{   // append the part up to the % sign
			ret.append (raw.substring (start, index));
			// there must be two hex digits after the percent sign
			if (index + 2 < length)
			{
				try
				{
					value = Integer.parseInt (raw.substring (index + 1, index + 3), 16);
					ret.append ((char)value);
					start = index + 3;
				}
				catch (NumberFormatException nfe)
				{
					ret.append ('%');
					start = index + 1;
				}
			}
			else
			{   // this case is actually illegal in a URI, but...
				ret.append ('%');
				start = index + 1;
			}
		}
		ret.append (raw.substring (start));

		return (ret.toString ());
	}

	/**
	 * Process a single page.
	 * @param filter The filter to apply to the collected nodes.
	 * @exception ParserException If a parse error occurs.
	 */
	protected void process (String filename, NodeFilter filter)
	throws
	ParserException
	{
		System.out.println ("processing " + filename);

		String url = null;

		try {
			PathTranslation transl = accessor.getPrimaryIndex().get( switchPathToRelative(getSource(), filename) );

			if (transl != null) {
				url = transl.getUrl();

				setCurrentUrl( url );

				write(filename, url);
			}

		} catch (DatabaseException e) {
			e.printStackTrace();
		} catch (ParserException e ) {
			e.printStackTrace();
			write(filename, url, "text/plain");
		}

	}

	protected void write(String filename, String url) throws ParserException {
		write (filename, url, null);
	}

	protected void write(String filename, String url, String mime) throws ParserException {
		NodeList list;

		try {

			int len = 0;
			byte[] buffer = null;
			String contentType = null;
			BufferedInputStream input = new BufferedInputStream( new FileInputStream(filename) );
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			if (mime == null) {
				// Handle initial buffer if present
				buffer = new byte[ identifier.getMinArrayLength() ];

				// Identify Content-Type
				input.read(buffer);

				contentType = identifier.identify(buffer, filename, null);
			} else {
				contentType = mime;
			}

			if (contentType == null)
				contentType = "text/plain";

			if ( contentType.equals("text/html") ) {

				// Generate HTTP Heander and write it
				byte[] header = generateHTTPHeader(contentType, "iso-8859-1").getBytes();
				baos.write(header);
				len = header.length;

				// fetch the page and gather the list of nodes
				mParser.setResource(filename);
				mParser.setEncoding("iso-8859-1");

				System.out.println("1-"+ mParser.getEncoding());

				try
				{
					list = new NodeList ();
					for (NodeIterator e = mParser.elements (); e.hasMoreNodes (); )
						list.add (e.nextNode ()); // URL conversion occurs in the tags

					System.out.println("2-"+ mParser.getEncoding());
				}
				catch (EncodingChangeException ece)
				{
					// fix bug #998195 SiteCatpurer just crashed
					// try again with the encoding now set correctly
					// hopefully mPages, mImages, mCopied and mFinished won't be corrupted
					mParser.reset ();
					list = new NodeList ();
					for (NodeIterator e = mParser.elements (); e.hasMoreNodes (); )
						list.add (e.nextNode ());
				} catch (ParserException e) {
					throw new ParserException();
				}

				if (null != getFilter())
					list.keepAllNodesThatMatch ( getFilter(), true);

				// --- BUG : header repeated ---
				//baos.write(buffer);
				//len += buffer.length;

				for (int i = 0; i < list.size(); i++) {				
					buffer = list.elementAt(i).toHtml().getBytes("ISO-8859-1");
					baos.write(buffer);

					len += buffer.length;
				}
				// Reading of non-HTML files
			} else {

				boolean is_binary = false;
				for (int i = 0; i < 30; i++) {
					if (buffer[i] < 32 || buffer[i] > 126) {
						if (buffer[i] != 10 && buffer[i] != 13) {
							int val = buffer[i];
							contentType = "binary/undefined";
							is_binary = true;
							break;
						}
					}
				}
				
				if ( is_binary ) {
					if (URLConnection.guessContentTypeFromName(filename) != null) {
						contentType = URLConnection.guessContentTypeFromName(filename);
					}
					
					// Generate HTTP Header and write it
					byte[] header = generateHTTPHeader(contentType).getBytes();
					baos.write(header);
					len = header.length;
					
					int read = 0;

					baos.write(buffer);
					len += buffer.length;

					buffer = new byte[4096];
					while ( (read = input.read(buffer)) != -1 ) {
						baos.write(buffer, 0, read);
						len += read;
					}
				} else {
					// Generate HTTP Header and write it
					byte[] header = generateHTTPHeader(contentType).getBytes();
					baos.write(header);
					len = header.length;
					
					input = new BufferedInputStream( new FileInputStream(filename) );
					StringBuilder builder = new StringBuilder();

					boolean inside_a = false;

					int i = 0;
					while ( (i = input.read()) != -1 ) {
						baos.write(i);
						len += 1;

						if (i == '<') {
							i = input.read();
							int i1 = input.read();
							baos.write(i);
							baos.write(i1);
							len += 2;

							if ( (i == 'a' || i == 'A') && i1 == ' ' ) {
								inside_a = true;
							}
						}

						if (inside_a) {
							int a1 = input.read();
							baos.write(a1);
							len += 1;

							if ( a1 == 'h' || a1 == 'H') {
								int a2 = input.read();
								baos.write(a2);
								len += 1;

								if ( a2 == 'r' || a2 == 'R') {
									int a3 = input.read();
									baos.write(a3);
									len += 1;

									if (a3 == 'e' || a3 == 'E') {
										int a4 = input.read();
										baos.write(a4);
										len += 1;

										if ( a4 == 'f' || a4 == 'F') {
											int a5 = input.read();
											baos.write(a5);
											len += 1;

											if ( a5 == '=') {

												int href_char = input.read();
												
												while (href_char == ' ' || href_char == '"') {
													baos.write(href_char);
													len += 1;
													href_char = input.read();
												}

												while (href_char != -1 && href_char != '"' && href_char != '>' && href_char != '<' ) {
													builder.append((char)href_char);
													href_char = input.read();
												}
												String link = builder.toString();
												
												try {
													if (link.startsWith("file://") || link.startsWith("../../")) {
														if ( link.startsWith("file://"))
															link = switchPathToRelative(getSource(), link);

														if ( link.indexOf('#') != -1) {
															String[] urlSplit = link.split("#");
															if ( urlSplit.length == 2) {
																link = accessor.getPrimaryIndex().get(urlSplit[0]).getUrl();
																link += '#' + urlSplit[1];
															}
														} else {
															try {
																link = accessor.getPrimaryIndex().get(link).getUrl();
															} catch (NullPointerException e ) {
																System.err.println("NOT IN DB: "+ link);
															}
														}
													} 
												} catch (DatabaseException e) {
													e.printStackTrace();
												}

												baos.write( link.getBytes("ISO-8859-1") );
												baos.write(href_char);
												len += link.getBytes("ISO-8859-1").length + 1;
												
												builder = new StringBuilder();
											}
										}
									}
								}
							}
						}
						inside_a = false;
					}
				}

			} 

			baos.write("\n".getBytes() );
			len += 1;

			System.out.println("URL: "+ url);

			String ip = extractIP(url);		

			if (url != null ) {
				getARCWriter().write( url, 
						contentType, 
						ip, 
						new File( filename ).lastModified(), 
						len, 
						baos);
			} else {
				System.err.println("Invalid file: "+ filename +"\twith URL: "+ url);
			}

			input.close();
			baos.close();


		} catch (FileNotFoundException e1) {
			e1.printStackTrace();

			String[] split = filename.split("\\.");

			if ( split.length == 2 ) {
				split[1] = split[1].toLowerCase();

				String lowcasePath = split[0] +'.'+ split[1]; 

				if ( !filename.equals(lowcasePath) )
					write(lowcasePath, url);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (StackOverflowError e) {
			System.err.println("OVERFLOW! @ "+ filename);
			e.printStackTrace();
			write(filename, url, "text/plain");
		}
	}

	/**
	 * 
	 * @param url
	 * @param contentType
	 * @return
	 */
	protected String generateHTTPHeader(String contentType, String encoding) {
		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/1.1 200 OK\r\n");
		builder.append("Content-Type: ");
		builder.append(contentType);
		builder.append("; charset=");
		builder.append(encoding);
		builder.append("\r\n\r\n");

		return builder.toString();
	}

	/**
	 * 
	 * @param url
	 * @param contentType
	 * @return
	 */
	protected String generateHTTPHeader(String contentType) {
		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/1.1 200 OK\r\n");
		builder.append("Content-Type: ");
		builder.append(contentType);
		builder.append("\r\n\r\n");

		return builder.toString();
	}

	/**
	 * 
	 * @param url
	 * @return
	 */
	protected String extractIP(String url) {
		String ip = DEFAULT_IP;

		try {
			Matcher matcher = pattern.matcher(url);
			matcher.matches();

			ip = matcher.group(1);
		} catch (IllegalStateException e) {
			/**
			 * It should fail a lot of times
			 * since the majority don't have the
			 * IP in the URL
			 */
			//e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ip;
	}

	/**
	 * TODO - comment
	 * @param basePath
	 * @param currentPath
	 * @return
	 */
	private static String switchPathToRelative(String basePath,
			String currentPath) {

		if ( currentPath.startsWith("file://") ) {
			try {
				basePath = new URL("file", "localhost", basePath).toExternalForm();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}

		String[] baseSplit = basePath.split("/");
		String[] currentSplit = currentPath.split("/");

		// TODO verificar os casos em que é dado explicitamente uma directoria

		StringBuilder relativePath = new StringBuilder();

		//TODO - receive html parser files as: file://localhost/*
		for (int i = baseSplit.length; i < (currentSplit.length - 1); i++) {
			relativePath.append("../");
		}
		for (int i = baseSplit.length; i < (currentSplit.length); i++) {
			relativePath.append(currentSplit[i]);
			if (i < currentSplit.length - 1)
				relativePath.append("/");
		}

		return relativePath.toString();
	}



	/**
	 * Link tag that rewrites the HREF.
	 * The HREF is changed to a local target if it matches the source.
	 */
	class LocalLinkTag extends LinkTag
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 5611752758157997437L;

		public void doSemanticAction ()
		throws
		ParserException
		{
			String link;

			// get the link
			link = getLink ();

			// alter the link
			try {
				if (link.startsWith("file://") || link.startsWith("../../")) {
					if ( link.startsWith("file://"))
						link = switchPathToRelative(getSource(), link);

					if ( link.indexOf('#') != -1) {
						String[] urlSplit = link.split("#");
						if ( urlSplit.length == 2) {
							link = accessor.getPrimaryIndex().get(urlSplit[0]).getUrl();
							link += '#' + urlSplit[1];
						}
					} else {
						try {
							link = accessor.getPrimaryIndex().get(link).getUrl();
						} catch (NullPointerException e ) {
							System.err.println("NOT IN DB: "+ link);
						}
					}
				} 
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
			setLink (link);
		}
	}

	/**
	 * Image tag that rewrites the SRC URL.
	 * If resources are being captured the SRC is mapped to a local target if
	 * it matches the source, otherwise it is convered to a full URL to point     String url;
     URL source;
     String path;
     File target;
     Boolean capture;
     int ret;
	 * back to the original site.
	 */
	class LocalImageTag extends ImageTag
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = -2579381165275496965L;

		public void doSemanticAction ()
		throws
		ParserException
		{
			String image;
			String original;

			// get the image url
			image = original = getImageURL ();

			try {

				if ( image.startsWith("file://") ) {
					image = switchPathToRelative(getSource(), image);
				}

				try {
					image = accessor.getPrimaryIndex().get(image).getUrl();
				} catch (NullPointerException e) {

					int lastSlashPosition = getCurrentUrl().lastIndexOf('/');
					String baseUrl = getCurrentUrl().substring(0, lastSlashPosition + 1);

					String imgUrl = baseUrl + image.substring( image.lastIndexOf("../") + 3);

					PathTranslation transl = new PathTranslation();

					String canonicalPath = getImageURL();
					if ( canonicalPath.startsWith("../../") ) {
						canonicalPath = getSource() +"/"+ canonicalPath.substring( canonicalPath.lastIndexOf("../") + 3 );
					} else {
						canonicalPath = canonicalPath.substring("file://localhost".length());
					}

					transl.setPath( canonicalPath );
					transl.setUrl( imgUrl );

					if ( !recoveryAccessor.getSecundaryIndex().contains( imgUrl) ) {
						recoveryAccessor.getPrimaryIndex().put(transl);
					} else {
						System.err.println("ALREADY IN RECOVERY DB: "+ imgUrl);
					}

					image = imgUrl;
				}

			} catch (DatabaseException e) {
				e.printStackTrace();
				image = original;
			} catch (IllegalArgumentException e) {
				image = original;
			} catch (Exception e) {
				e.printStackTrace();
				image = original;
			}
			// alter the link
			setImageURL (image);
		}
	}

	/**
	 * Base tag that doesn't show.
	 * The toHtml() method is overridden to return an empty string,
	 * effectively shutting off the base reference.
	 */
	class LocalBaseHrefTag extends BaseHrefTag
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 6128054296731638103L;

		// we don't want to have a base pointing back at the source page
		public String toHtml ()
		{
			//TODO - check this method to see if it has an negative effect
			return ("CAUTION");
		}
	}

	public void traverse ()
	{
		traverse(getSource(), "");

		try {

			// write the documents without an embedded URL using the URL from the
			// recovery DB that was generated.
			try {
				EntityCursor<PathTranslation> cursor = recoveryAccessor.getSecundaryIndex().entities();

				for ( PathTranslation o : cursor ) {
					write(o.getPath(), o.getUrl());
				}

				cursor.close();

			} catch (DatabaseException e) {
				e.printStackTrace();
			} catch (ParserException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} 

			writer.close();

			env.close();
			recoveryEnv.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Perform the capture.
	 */
	public void traverse (String filename, String tabulation)
	{
		File f = new File(filename);

		if (f.isDirectory()) {

			File[] a = f.listFiles();
			for (File file : a) {
				if (file.isDirectory()) {
					System.out.println(tabulation +" DIR: " + file.getAbsolutePath());
					traverse(file.getAbsolutePath(), tabulation +"-");
				} else {
					System.out.println(tabulation +" File: " + file);
					try {
						process( file.getAbsolutePath(), getFilter() );
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			try {
				process( f.getAbsolutePath(), getFilter() );
			} catch (ParserException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Mainline to capture a web site locally.
	 * @param args The command line arguments.
	 * There are three arguments the web site to capture, the local directory
	 * to save it to, and a flag (true or false) to indicate whether resources
	 * such as images and video are to be captured as well.
	 * These are requested via dialog boxes if not supplied.
	 * @exception MalformedURLException If the supplied URL is invalid.
	 * @exception IOException If an error occurs reading the page or resources.
	 * @throws DatabaseException 
	 */
	public static void main (String[] args)
	throws
	MalformedURLException,
	IOException, DatabaseException
	{
		ArcConverter worker;

		worker = new ArcConverter ();

		try {
		/* Dir to be used as the source of files */
		worker.setSource (args[0]);

		/* Dir where the ARC files are written */
		worker.setTarget (args[1]);

                File[] targetPath = new File[] { new File( worker.getTarget() ) };

                ARCWriter w = new ARCWriter( new AtomicInteger(),                       
                	Arrays.asList(targetPath),
                	worker.BASE_NAME +"-"+ worker.PREFIX,
                        true,
                        ARCConstants.DEFAULT_MAX_ARC_FILE_SIZE
                );

                worker.setARCWriter(w);

		/* The path to the database with the info about local path <-> URL */
		worker.env = new DatabaseEnvironment();
                worker.env.setup( new File(args[2]), true);

                worker.setDatabase( new PathTranslationDataAccessor(worker.env.getEntityStore()) );

		/* The path where the recovery database will be created */
		worker.recoveryEnv = new DatabaseEnvironment();
                worker.recoveryEnv.setup( new File(args[3]), false);

                worker.setRecoveryDatabase( new PathTranslationDataAccessor(worker.recoveryEnv.getEntityStore()) );

		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("ArcConverter: Missing Parameters!");
			System.err.println("To use: java ArcConverter <source_dir> <dest_dir> <db_dir> <recovery_db_dir>");
			System.exit(1);
		}
	
		worker.traverse ();

		System.exit (0);
	}
}
