package edu.berkeley.cs.cs162;

import java.sql.SQLException;

/**
 * Access the authentication database.
 * @author stevedh
 *
 */
public class ChatAuthenticator {
	ChatServer server=null;
	int authenticate(String username, String credentials) {
		try {
			return server.db.loginAuthenticator(username, credentials);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			return -1;
		}
	}
}
