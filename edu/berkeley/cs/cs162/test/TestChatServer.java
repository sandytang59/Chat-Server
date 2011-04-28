package edu.berkeley.cs.cs162.test;

/**
 * 
 * @author jortiz@cs.berkeley.edu
 * 
 */

import java.util.Date;
import java.util.Hashtable;
import java.util.Random;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import edu.berkeley.cs.cs162.BaseUser;
import edu.berkeley.cs.cs162.ChatServer;
import edu.berkeley.cs.cs162.LoginError;

public class TestChatServer {
	private static transient final Logger logger = Logger
			.getLogger(TestChatServer.class.getPackage().getName());

	/**
	 * Logs the events of a user logging into the ChatServer. This should only
	 * be called AFTER the user has been accepted by the ChatServer.
	 * 
	 * @param username
	 *            user logging the event.
	 * @param time
	 *            time of the event.
	 */
	public static synchronized void logUserLogin(String username, Date time) {
		if (username != null && time != null && username.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.info(utime + ": " + username + " LOGIN");
		}
	}

	/**
	 * Logs the events of a user logging into the ChatServer. This should only
	 * be called AFTER the user has been accepted by the ChatServer.
	 * 
	 * @param username
	 *            user logging the event.
	 * @param time
	 *            time of the event.
	 */
	public static synchronized void logUserLoginFailed(String username,
			Date time, LoginError e) {
		if (username != null && time != null && username.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.warning(utime + ": " + username + " LOGIN_FAILED [" + e
					+ "]");
		}
	}

	/**
	 * Logs the logout event. This should only be called AFTER the user has been
	 * released by the ChatServer successfully.
	 * 
	 * @param username
	 *            user logging the event
	 * @param time
	 *            time of the event
	 */
	public static synchronized void logUserLogout(String username, Date time) {
		if (username != null && time != null && username.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.info(utime + ": " + username + " LOGOUT");
		}
	}

	/**
	 * Logs the events of a user logging into the group. This should only be
	 * called AFTER the user has been accepted by the group.
	 * 
	 * @param groupname
	 *            name of the group.
	 * @param username
	 *            user logging the event.
	 * @param time
	 *            time of the event.
	 */
	public static synchronized void logUserJoinGroup(String groupname,
			String username, Date time) {
		if (username != null && time != null && username.length() > 0
				&& groupname != null && groupname.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.warning(utime + ": " + username + " joins " + groupname
					+ " GROUP_JOIN");
		}
	}

	/**
	 * Logs the events of a user logging out of the group.
	 * 
	 * @param groupname
	 *            name of the group.
	 * @param username
	 *            user logging the event.
	 * @param time
	 *            time of the event.
	 */
	public static synchronized void logUserLeaveGroup(String groupname,
			String username, Date time) {
		if (username != null && time != null && username.length() > 0
				&& groupname != null && groupname.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.warning(utime + ": " + username + " leaves " + groupname
					+ " GROUP_LEAVE");
		}
	}

	/**
	 * This should be called when the user attempts to send a message to the
	 * chat server (after the call is made).
	 * 
	 * @param msg
	 *            the string representation of the message. SRC DST
	 *            TIMESTAMP_UNIXTIME SQN example: alice bob 1298489721 23
	 */
	public static synchronized void logUserSendMsg(String username, String msg) {
		if (username != null && username.length() > 0) {
			Date time = new Date();
			long utime = time.getTime() / 1000;
			logger.info(utime + ": " + username + "::" + msg + " MESSAGE_SENT");
		}
	}

	/**
	 * If, for any reason, the ChatServer determines that the message cannot be
	 * delivered. This message should be called to log that event.
	 * 
	 * @param msg
	 *            string representation of the message SRC DST
	 *            TIMESTAMP_UNIXTIME SQN example: alice bob 1298489721 23
	 * @param time
	 *            time when the event occurred.
	 */
	public static synchronized void logChatServerDropMsg(String msg, Date time) {
		if (time != null) {
			long utime = time.getTime() / 1000;
			logger.info(utime + ": " + msg + " CHATSERVER_DROP_MSG");
		}
	}

	/**
	 * When the user receives a message, this method should be called.
	 * 
	 * @param msg
	 *            string representation of the message. SRC DST
	 *            TIMESTAMP_UNIXTIME SQN example: alice bob 1298489721 23
	 * @param time
	 *            time when the event occurred.
	 */
	public static synchronized void logUserMsgRecvd(String username,
			String msg, Date time) {
		if (username != null && time != null && username.length() > 0) {
			long utime = time.getTime() / 1000;
			logger.info(utime + ": " + username + "::" + msg
					+ " MESSAGE_RECEIVED");
		}
	}
}
