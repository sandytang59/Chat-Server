package edu.berkeley.cs.cs162;

/**
 * Queue of potential action types for a ChatUser to take.
 * @author stevedh
 *
 */
public enum ActionType {
	ACTION_SEND,     /* send a message */
	ACTION_RECEIVE,  /* receive a message */
	ACTION_JOIN,     /* join a group */
	ACTION_LEAVE,    /* leave a group */
	ACTION_SHUTDOWN,
}
