package pt.tomba.roteiro2arc.db;

import java.io.File;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;

public class DatabaseEnvironment {

	private Environment env;

	private EntityStore store;

	// Our constructor does nothing
	public DatabaseEnvironment() {
	}

	// The setup() method opens the environment and store
	// for us.
	public void setup(File envHome, boolean readOnly) throws DatabaseException {
		EnvironmentConfig myEnvConfig = new EnvironmentConfig();
		StoreConfig storeConfig = new StoreConfig();
		
		myEnvConfig.setReadOnly(readOnly);
		storeConfig.setReadOnly(readOnly);
		
		// If the environment is opened for write, then we want to be
		// able to create the environment and entity store if
		// they do not exist.
		myEnvConfig.setAllowCreate(!readOnly);
		storeConfig.setAllowCreate(!readOnly);
		storeConfig.setDeferredWrite(!readOnly);
		
		// Open the environment and entity store
		env = new Environment(envHome, myEnvConfig);
		store = new EntityStore(env, "EntityStore", storeConfig);
	}

	//     Return a handle to the entity store
	public EntityStore getEntityStore() {
		return store;
	}

	//     Return a handle to the environment
	public Environment getEnv() {
		return env;
	}

	//  Close the store and environment.
	public void close() {
		if (store != null) {
			try {
				store.close();
			} catch (DatabaseException dbe) {
				throw new RuntimeException("Error closing store: " + dbe.toString());
			}
		}
		if (env != null) {
			try {
				// Finally, close the environment.
				env.close();
			} catch (DatabaseException dbe) {
				throw new RuntimeException("Error closing MyDbEnv: " + dbe.toString());
			}
		}
	}
}
