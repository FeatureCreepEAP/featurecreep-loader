package featurecreep.loader;

import java.security.Permission;

/**
 * Our custom ExitManager does not allow the VM to exit, but does allow itself
 * to be replaced by the original security manager.
 * 
 * @author Andrew Thompson
 *         https://stackoverflow.com/questions/5401281/preventing-system-exit-from-api
 */
public class ExitManager extends SecurityManager {

	SecurityManager original;

	ExitManager(SecurityManager original) {
		this.original = original;
	}

	/** Deny permission to exit the VM. */
	public void checkExit(int status) {
		throw (new SecurityException());
	}

	/**
	 * Allow this security manager to be replaced, if fact, allow pretty much
	 * everything.
	 */
	public void checkPermission(Permission perm) {
	}

	public SecurityManager getOriginalSecurityManager() {
		return original;
	}
}