package pt.tomba.roteiro2arc;

//ExtractURL - Search the bottom of the archived files for URL (that should be
//the original one) and stores in a DB a relation <local path>/<url>.
//Original Creator: David Cruz <david.cruz@fccn.pt>
//For: SAW Group - FCCN <sawfccn@fccn.pt>
//Copyright (C) 2009
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlparser.Parser;

import pt.tomba.roteiro2arc.db.DatabaseEnvironment;
import pt.tomba.roteiro2arc.db.PathTranslationDataAccessor;
import pt.tomba.roteiro2arc.model.PathTranslation;

import com.sleepycat.je.DatabaseException;

//TODO - index.htm of 'Roteiro' use links that in the following structure: ^http/\d{1-2}/\d{1-2}/\d{1-2}(\.\w{3})?$

public class ExtractURL {
	private static Pattern pattern;

	private static String in_path;

	private static Parser parser;
	
	private static DatabaseEnvironment env;
	
	private static PathTranslationDataAccessor accessor;

	public static void main(String[] args) throws FileNotFoundException, DatabaseException {
		in_path = args[0];
		
		System.out.println(args[1]);
		
		env = new DatabaseEnvironment();
		env.setup( new File(args[1]), false );
		
		accessor = new PathTranslationDataAccessor( env.getEntityStore() );

		System.out.println("in> " + in_path);

		parser = new Parser();

		pattern = Pattern.compile(".*<a .*?>(.*?)<.*");

		traverse(in_path);
		
		env.close();
	}

	public static void traverse(String fileName) {
		traverse(fileName, "");
	}
	
	public static void traverse(String fileName, String tabulation) {
		File f = new File(fileName);

		if (f.isDirectory()) {
			
			File[] a = f.listFiles();
			for (File file : a) {
				if (file.isDirectory()) {
					System.out.println(tabulation +" DIR: " + file.getAbsolutePath());
					traverse(file.getAbsolutePath(), tabulation +"-");
				} else {
					System.out.println(tabulation +" File: " + file);
						extract(file.getAbsolutePath());
				}
			}
		} else {
			extract(f.getAbsolutePath());
		}
	}

	public static void extract(String fileName) {

		String pageURL = extractPageURL(fileName);
		
		if ( pageURL != null ) {
		String relativePath = switchPathToRelative(in_path, fileName);

		try {
			PathTranslation transl = new PathTranslation();
			transl.setPath(relativePath);
			transl.setUrl(pageURL);
			
			accessor.getPrimaryIndex().put(transl);
			System.out.println("key: "+ relativePath +"\tURL: "+ accessor.getPrimaryIndex().get(relativePath).getUrl() );
			
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		
		}

	}

	public static String extractPageURL(String fileName) {
		BufferedReader reader = null;
		Matcher match = null;
		String url = null;

		try {
			reader = new BufferedReader(new FileReader(fileName));

			String line = null;
			String previousLine = null;
			while ((line = reader.readLine()) != null) {
				previousLine = line;
			}		

			if (previousLine != null) {
				match = pattern.matcher(previousLine);
				
				match.matches();

				url = match.group(1).trim();
				
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return url;
	}



	private static String switchPathToRelative(String basePath,
			String currentPath) {
		String[] baseSplit = basePath.split("/");
		String[] currentSplit = currentPath.split("/");

		// TODO verify if a dir is explicitly given

		StringBuilder relativePath = new StringBuilder();

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
}
