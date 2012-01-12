package pt.tomba.roteiro2arc.arc.test;

//TestDB - Tests Berkeley DB to ensure data is being stored.
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

import java.io.File;

import pt.tomba.roteiro2arc.db.DatabaseEnvironment;
import pt.tomba.roteiro2arc.db.PathTranslationDataAccessor;
import pt.tomba.roteiro2arc.model.PathTranslation;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityCursor;

public class TestDB {

	/**
	 * @param args
	 * @throws DatabaseException 
	 * @throws DatabaseException 
	 */
	public static void main(String[] args) throws DatabaseException {
		PathTranslationDataAccessor accessor;
		DatabaseEnvironment env = new DatabaseEnvironment();
		
		env.setup(new File(args[0]), false);
		
		accessor = new PathTranslationDataAccessor(env.getEntityStore());
		
		PathTranslation transl = new PathTranslation();
		transl.setPath("/home/");
		transl.setUrl("http://localhost/home/");
		accessor.getPrimaryIndex().put(transl);
		
		PathTranslation t2 = new PathTranslation();
		t2.setPath("/home/");
		t2.setUrl("http://localhost/home/2");
		accessor.getPrimaryIndex().put(t2);
		
		PathTranslation t3 = new PathTranslation();
		t3.setPath("/home/3");
		t3.setUrl("http://localhost/home/");
		accessor.getPrimaryIndex().put(t3);
		
		PathTranslation pt = accessor.getPrimaryIndex().get("/home/");
		EntityCursor<PathTranslation> cursor = accessor.getSecundaryIndex().entities();
		
		for ( PathTranslation o : cursor ) {
			System.out.println(o.getUrl());
		}
		
		cursor.close();
		
		env.close();
	}

}
