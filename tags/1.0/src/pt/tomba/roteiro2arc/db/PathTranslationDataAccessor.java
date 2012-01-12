package pt.tomba.roteiro2arc.db;

//PathTranslationDataAccessor - Define the data acessors for PathTranslation objects
//in BDB data stores.
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

import pt.tomba.roteiro2arc.model.PathTranslation;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;

public class PathTranslationDataAccessor {

	private PrimaryIndex<String, PathTranslation> index;
	private SecondaryIndex<String, String, PathTranslation> sec_index;
	
    // Open the indices
    public PathTranslationDataAccessor(EntityStore store) throws DatabaseException {
        // Primary key for Inventory classes
        index = store.getPrimaryIndex(
            String.class, PathTranslation.class);
        
        sec_index = store.getSecondaryIndex(
        		index, String.class, "url");
    }
    
    public PrimaryIndex<String,PathTranslation> getPrimaryIndex() {
    	return index;
    }
    
    public SecondaryIndex<String, String, PathTranslation> getSecundaryIndex() {
    	return sec_index;
    }
}
