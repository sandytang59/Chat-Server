package edu.berkeley.cs.cs162;

import java.io.Serializable;
/**
 * Encapsulate everything about a message being sent between threads.
 * @author stevedh, jortiz
 *
 */
public class Message implements Serializable{
	private String m_message;
	private String m_src;
	private String m_dst;
	private int m_seq;
	public long m_timestamp;
	private MessageType m_type;
	private String m_groupname;
	public int groupid;

	public Message(String src, String dst, String message, int seq, MessageType type) {
		// throws MessageSizeException, InvalidDestinationException {
		m_src = src;
		m_dst = dst;
		setMessage(message);
		setSeq(seq);
		m_timestamp = System.currentTimeMillis()/1000L;
		m_type = type;
		groupid=-1;
	}

	public Message(String src, String dst, String message, int seq, MessageType joinleave, String groupname) 
		//throws Exception
	{
		//if(joinleave != MessageType.JOIN_GROUP && joinleave != MessageType.LEAVE_GROUP)
		//	throw new Exception("Must be join_group or leave_group request");

		m_src = src;
		m_dst = dst;
		setMessage(message);
		setSeq(seq);
		m_timestamp = System.currentTimeMillis()/1000L;
		m_type = joinleave;
		m_groupname = groupname;
		groupid=-1;
	}

	public MessageType getType(){
		return m_type;
	}

	public int getSqn(){
		return m_seq;
	}
	
	public void setMessage(String message) {
		m_message = message;
	}

	public String getMessage() {
		return m_message;
	}
	
	/**
	 * @return the destination of the message: the name of a user or a group
	 */
	public String getDest() {
		return m_dst;
	}

	/**
	 * @return the name of the message source
	 * */
	public String getSrc() {
		return m_src;
	}

	public String getGroupName(){
		if(m_type == MessageType.JOIN_GROUP || m_type == MessageType.JOIN_GROUP_OK_CREATE || 
				m_type == MessageType.JOIN_GROUP_BAD_GROUP || m_type == MessageType.JOIN_GROUP_FAIL_FULL ||
				m_type == MessageType.LEAVE_GROUP || m_type == MessageType.LEAVE_GROUP_BAD_GROUP ||
				m_type == MessageType.LEAVE_GROUP_OK || m_type == MessageType.LEAVE_GROUP_NOT_MEMBER)
		{
			return m_groupname;
		} else {
			return null;
		}
	}

	/**
	 * @return the message sent by the user
	 */
	public String toString() {
		return getSrc() + " " + getDest() + " " + m_timestamp + " " + getSeq() + " [" + m_message + "]";
	}

	public void setSeq(int m_seq) {
		this.m_seq = m_seq;
	}

	public int getSeq() {
		return m_seq;
	}
}
