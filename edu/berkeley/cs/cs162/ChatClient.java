package edu.berkeley.cs.cs162;

/**
 * Client accepts input from standard in.  Please refer to CS162 project 2 specification for
 * acceptable user input.
 *
 * @author jortiz
 *
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import edu.berkeley.cs.cs162.test.TestChatServer;

public class ChatClient{
	
	private static Socket socket = null;
	private static String username = null;
	private static String m_host = null;
	private static int m_port = -1;
	private static boolean sleeping = false;

	private static Vector<Message> sleepMsgBuf = null;	// message buffer to handle client sleep event
	private static Semaphore syncSemaphore = null;		//semaphore to regulate sync-message processing

	public ChatClient(){
		sleepMsgBuf = new Vector<Message>();
		syncSemaphore = new Semaphore(1, true);
	}

	public static void main(String[] args){
		try {
			//pipes that communicate with the writer thread and the reader thread
			PipedInputStream wPipeIS = null;
			PipedOutputStream wPipeOS = null;
			
			PipedInputStream rPipeIS = null;
			PipedOutputStream rPipeOS = null;
			
			ChatClient chatClient = new ChatClient();
			
			//instantiate the reader and writer threads	
			WriterThread writerThread = null;
			ReaderThread readerThread = null;
			
			InputStreamReader isReader = new InputStreamReader(System.in);
			BufferedReader bufReader = new BufferedReader(isReader);
			ObjectOutputStream wPipeOOS = null;
			while(true){
				try {
					String inputStr = null;
					BufferedWriter bufWriter =  new BufferedWriter(new OutputStreamWriter(System.out));
					String prompt = ">  ";
					bufWriter.write(prompt, 0, prompt.length());
					bufWriter.flush();

					if((inputStr=bufReader.readLine()) != null){
						System.out.println(inputStr);
						Message m = chatClient.parseInput(inputStr);
						if(m!=null && inputStr.startsWith("connect") && m.getType()==MessageType.CONNECT_OK){
							if(writerThread==null || writerThread.getState() != Thread.State.NEW){
								wPipeIS = new PipedInputStream();
								wPipeOS = new PipedOutputStream();
								wPipeIS.connect(wPipeOS);
								writerThread = new WriterThread(wPipeIS);
							}
							if(readerThread == null || readerThread.getState() != Thread.State.NEW){
								rPipeIS = new PipedInputStream();
								rPipeOS = new PipedOutputStream();
								rPipeIS.connect(rPipeOS);
								readerThread = new ReaderThread(bufWriter);
							}
							wPipeOOS = new ObjectOutputStream(wPipeOS);
							
							writerThread.start();
							readerThread.start();
						} else if(m!=null){
							if(m.getType() != MessageType.SEND_MSG){
								//System.out.println("Acquiring semaphore");
								syncSemaphore.acquire();
							}
							if(!writerThread.isAsleep()){
								if(sleepMsgBuf.size()>0){
									sleepMsgBuf.add(m);
									int i=0;
									while(sleepMsgBuf.size()>0 && !writerThread.isAsleep()){
										Message thisMsg = (Message)sleepMsgBuf.elementAt(0);
										wPipeOOS.writeObject(thisMsg);
										wPipeOOS.flush();
										sleepMsgBuf.remove(0);
									}
								} else {
									wPipeOOS.writeObject(m);
									wPipeOOS.flush();
								}
							} else {
								sleepMsgBuf.add(m);
							}
						} else if(inputStr.equalsIgnoreCase("exit")){
							System.exit(1);
						} else if (inputStr.startsWith("sleep")){
							try {
								StringTokenizer tokens = new StringTokenizer(inputStr, " ");
								Vector<String> tvec = new Vector<String>();
								while(tokens.hasMoreElements())
									tvec.add(tokens.nextToken());
								if(tvec.size() != 2)
									return;
								writerThread.setAsleep(Integer.parseInt(tvec.elementAt(1)));
							} catch(Exception e){
								e.printStackTrace();
							}
						} else {
							System.out.println("Invalid input string: " + inputStr + " " + inputStr.length());
						}
					}
				} catch (Exception e){
					e.printStackTrace();
				}
			}
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	 *  Parses user input string.
	 * @param inputStr user input string.
	 * @return Message object translation of the input string or null if the input string is unknown.
	 */
	public Message parseInput(String inputStr){
		Message transMsg = null;
		try {
			StringTokenizer tokenizer = new StringTokenizer(inputStr, " ");
			Vector<String> tvec = new Vector<String>();
			while(tokenizer.hasMoreTokens())
				tvec.add(tokenizer.nextToken());

			if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("connect") && tvec.size()==2){
				StringTokenizer hostportTokenizer = new StringTokenizer(tvec.elementAt(1), ":");
				Vector<String> hpvec = new Vector<String>();
				while(hostportTokenizer.hasMoreTokens())
					hpvec.add(hostportTokenizer.nextToken());

				try {
					if(hpvec.size()==2){
						int port = Integer.parseInt(hpvec.elementAt(1));
						String host = hpvec.elementAt(0);
						socket = new Socket(host, port);
						if(socket.isConnected()) 
							System.out.println("connect OK");
						transMsg = new Message("new_user", "ChatServer", "connect OK", -1, MessageType.CONNECT_OK);
						m_host = host;
						m_port = port;
						
					}
				} catch(Exception e){
					//e.printStackTrace();
					System.out.println("connect REJECTED");
					transMsg = new Message("new_user", hpvec.elementAt(0), "connect OK", -1, MessageType.CONNECT_REJECTED);
				}
			} else if (tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("readlog")){
				transMsg = new Message(username, "ChatServer", "readlog", -1, MessageType.READ_LOG);
			} else if (tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("disconnect")){
				transMsg = new Message(username, "ChatServer", "disconnect", -1, MessageType.DISCONNECT);
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("adduser") && tvec.size()==3){
				transMsg = new Message(tvec.elementAt(1), "ChatServer", tvec.elementAt(2), -1, MessageType.ADD_USER);
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("login") && tvec.size()==3){
				transMsg = new Message(tvec.elementAt(1), "ChatServer", tvec.elementAt(2), -1, MessageType.LOGIN);
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("logout")){
				transMsg = new Message(username, "ChatServer", "", -1, MessageType.LOGOUT);
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("join") && tvec.size()==2){
				transMsg = new Message(username, "ChatServer", "", -1, MessageType.JOIN_GROUP, tvec.elementAt(1));
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("leave") && tvec.size()==2){
				System.out.println("sending leave_group: " + tvec.elementAt(1));
				transMsg = new Message(username, "ChatServer", "", -1, MessageType.LEAVE_GROUP, tvec.elementAt(1));
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("send") && tvec.size()>=4){
				StringBuffer msgBuf = new StringBuffer();
				for(int i=3; i<tvec.size(); i++){

					int length = tvec.elementAt(i).length();
					if(i==3 && tvec.elementAt(i).startsWith("\"")){
						tvec.set(i, tvec.elementAt(i).substring(1, length));
						length = tvec.elementAt(i).length();

						if(tvec.size()==4) {
							tvec.set(3, tvec.elementAt(i).substring(0, length-1));
						}
					} else if(i==tvec.size()-1 && tvec.elementAt(i).endsWith("\"")){
						tvec.set(i, tvec.elementAt(i).substring(0, length-1));
					}

					
					msgBuf.append(tvec.elementAt(i));
					if(i != tvec.size()-1)
						msgBuf.append(" ");
					//System.out.println("Constructed msg, " + i +  ": " + msgBuf.toString());
				}
				System.out.println("Sending message to: " + tvec.elementAt(1));
				transMsg = new Message(username, tvec.elementAt(1), msgBuf.toString(), Integer.parseInt(tvec.elementAt(2)), MessageType.SEND_MSG);
				System.out.println("msg: " + transMsg.toString());
			} else if(tvec.size()>0 && tvec.elementAt(0).equalsIgnoreCase("sleep") && tvec.size()==2){
				//something
			}
		} catch(Exception e){
			e.printStackTrace();
		}

		return transMsg;
	}


	/**
	 * Used to log client-associated events.
	 */
	private static void log(Message m){
		if(m !=null){
			switch(m.getType()){
				case LOGIN_OK:
					TestChatServer.logUserLogin(m.getSrc(), new Date());
					break;
				case LOGOUT_OK:
					TestChatServer.logUserLogout(m.getSrc(), new Date());
					break;
				case JOIN_GROUP_OK_JOIN:
					TestChatServer.logUserJoinGroup(m.getGroupName(), m.getSrc(), new Date());
					break;
				case JOIN_GROUP_OK_CREATE:
					TestChatServer.logUserJoinGroup(m.getGroupName(), m.getSrc(), new Date());
					break;
				case LEAVE_GROUP_OK:
					TestChatServer.logUserLeaveGroup(m.getGroupName(), m.getSrc(), new Date());
					break;
				case SEND_MSG_OK:
					TestChatServer.logUserSendMsg(m.getSrc(), m.toString());
					break;
			}
		}
	}

	/**
	 *  Seperate thread that writes messages to the server
	 */
	private static class WriterThread extends Thread{
		private ObjectOutputStream m_server_oos = null;
		private PipedInputStream m_source = null;
		private boolean asleep = false;
		private boolean setAsleep = false;
		private int sleepTime = -1;

		public WriterThread(PipedInputStream pipedIS){
			m_source = pipedIS;
		}

		public synchronized void setAsleep(int millis){
			setAsleep = true;
			sleepTime = millis;
		}

		public synchronized boolean isAsleep(){
			return asleep;
		}

		public void run(){
			try {
				if(setAsleep && sleepTime >0){
					synchronized(this){
						asleep=true;
						setAsleep = false;
					}
					this.sleep(sleepTime);
					synchronized (this){
						asleep = true;
						sleepTime=-1;
					}
				}
				if(socket != null){
					m_server_oos = new ObjectOutputStream(socket.getOutputStream());
					System.out.println("writer set up");
				}
				ObjectInputStream localIS = new ObjectInputStream(m_source);
				Message thisMsg = null;
				while(socket != null && socket.isConnected() && (thisMsg = (Message)localIS.readObject()) != null){
					//System.out.println("Heard message from user: " + thisMsg.toString());
					m_server_oos.writeObject(thisMsg);
					//System.out.println("releasing semaphore");
					syncSemaphore.release();
				}
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	}


	/**
	 * Reader thread reads responses from the server and writes it to standard output.
	 */
	private static class ReaderThread extends Thread{

		private ObjectInputStream m_server_ois = null;
		private BufferedWriter m_bufWriter = null;

		public ReaderThread(BufferedWriter bufWriter){
			m_bufWriter = bufWriter;
		}

		public void run(){
			Message rcvMsg = null;
			try {
				if(socket != null) {
					//System.out.println("Reader setup attempt...");
					m_server_ois = new ObjectInputStream(socket.getInputStream());
					//System.out.println("reader set up");
				}
				while(socket != null && socket.isConnected() && (rcvMsg = (Message)m_server_ois.readObject()) != null){
					String msgStr = rcvMsg.getMessage();
					if(rcvMsg.getType()==MessageType.LOGIN_OK){
						username = rcvMsg.getDest();
						//System.out.println("login ok!");
					}

					log(rcvMsg);
					msgStr = "\n" + msgStr + "\n> ";
					m_bufWriter.write(msgStr, 0, msgStr.length());
					m_bufWriter.flush();
				}
			} catch(Exception e){
				//e.printStackTrace();
				if(socket == null)
					System.out.println("Yeah, the socket is null");
			}
		}
	}

}
