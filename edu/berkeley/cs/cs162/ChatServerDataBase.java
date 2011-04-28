package edu.berkeley.cs.cs162;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

public class ChatServerDataBase {
	
	private Connection connection = null;
	private String database = "";
	
	public boolean connect(String server, String dbname,String user, String password) throws ClassNotFoundException, SQLException {
		Class.forName("com.mysql.jdbc.Driver");
		// Setup the connection with the DB
		connection = DriverManager.getConnection("jdbc:mysql://"+server+"/"+dbname+"?user="+ user +"&password=" + password);
		database = dbname;
		return true;
	}
	
	private PreparedStatement deleteMaker(String table,  String idTitle, int idValue,String other) throws SQLException {
		String query="DELETE FROM " +table + " WHERE "+idTitle+ " = ?"+(other.equals("") ? "":" AND "+other) ;
		PreparedStatement preparedStatement = connection.prepareStatement(query);
		preparedStatement.setInt(1, idValue);
		return preparedStatement;
		
	}
	
	private PreparedStatement insertMaker(String table, int count, String[] args,Object[] vals) throws SQLException {
		String query="INSERT INTO " +table + " (" ;
		for (int i=0;i<count;i++) {
			query+="`"+args[i]+"`"+(i==count-1 ? "" : "," );
		}
		query +=") values (";
		for (int i=0;i<count;i++) {
			query+=" ? "+(i==count-1 ? " )" : "," );
		}
		PreparedStatement preparedStatement = connection.prepareStatement(query,Statement.RETURN_GENERATED_KEYS);
		for (int i=0;i<count;i++) {
			preparedStatement.setObject(i+1, vals[i]);
		}
		return preparedStatement;
		
	}
	
	private PreparedStatement selectMaker(String what,String table, String idTitle, int idValue,String other) throws SQLException {
		String query="SELECT "+what+" FROM " +table+ " WHERE " +idTitle + " = ?"+(other.equals("") ? "":" AND "+other);
		PreparedStatement preparedStatement = connection.prepareStatement(query);
		preparedStatement.setInt(1, idValue);
		return preparedStatement;
		
	}
	
	public void addUser(String username, String password) throws SQLException {
		//add username and password hash combination

		
		PreparedStatement preparedStatement = null;
		
		preparedStatement = connection.prepareStatement("INSERT INTO chatserver_logins (`username`, `password_hash`)  values (?, MD5(?))");
		preparedStatement.setString(1, username);
		preparedStatement.setString(2, password);
		preparedStatement.executeUpdate();
	}
	
	public int loginAuthenticator(String username, String password) throws SQLException {
		
		ResultSet resultSet = null;
		PreparedStatement preparedStatement = null;
		
		preparedStatement = connection.prepareStatement("SELECT user_id FROM chatserver_logins WHERE username = ?  AND password_hash = MD5(?)");
		preparedStatement.setString(1, username);
		preparedStatement.setString(2, password);
		
		resultSet = preparedStatement.executeQuery();
		if (resultSet.isBeforeFirst()) {
			resultSet.next();
			return resultSet.getInt("user_id");
		} else {
			return -1;
		}
		
	}
	
	public int getUserid(String username) throws SQLException {
		
		ResultSet resultSet = null;
		PreparedStatement preparedStatement = null;
		
		preparedStatement = connection.prepareStatement("SELECT user_id FROM chatserver_logins WHERE username = ? ");
		preparedStatement.setString(1, username);
		
		resultSet = preparedStatement.executeQuery();
		if (resultSet.isBeforeFirst()) {
			resultSet.next();
			return resultSet.getInt("user_id");
		} else {
			return -1;
		}
		
	}

	public int delUserLoggedMessages(int userid) throws SQLException {
		PreparedStatement ps=this.deleteMaker("offline_messages", "to_user_id", userid, "");
		return ps.executeUpdate();
	}

	public int delGroupLoggedMessages(int userid) throws SQLException {
		PreparedStatement ps=this.deleteMaker("offline_group_messages", "to_user_id", userid, "");
		return ps.executeUpdate();
	}
	
	public ResultSet readUserLoggedMessages(int userid) throws SQLException {
		PreparedStatement ps=selectMaker("m.*","offline_messages m","m.to_user_id",userid,"");
		return ps.executeQuery();
	}

	public ResultSet readGroupLoggedMessages(int userid) throws SQLException {
		PreparedStatement ps=selectMaker("m.*, g.groupname","offline_group_messages m, groups g","m.to_user_id",userid,"g.group_id = m.group_id");
		return ps.executeQuery();
	}
	

	public boolean sendOfflineMessage(Message m) throws SQLException {
		// TODO Auto-generated method stub
		int uid=this.getUserid(m.getDest());
		if(uid>-1) {
			this.sendOfflineMessage(uid, m.getSrc(), m.getMessage());
			return true;
		} else return false;
	}
	
	public int sendOfflineMessage(int touser,String from,String msg) throws SQLException {
		String[] args= {"to_user_id","from","text"};
		Object[] vals= {Integer.valueOf(touser),from,msg};
		PreparedStatement ps=insertMaker("offline_messages", 3, args,vals);
		
		return ps.executeUpdate();
	}
	
	public boolean sendOfflineGroupMessage(Message m) throws SQLException {
		// TODO Auto-generated method stub
		int uid=this.getUserid(m.getDest());
		if(uid>-1) {
			this.sendOfflineGroupMessage(uid,m.groupid, m.getSrc(), m.getMessage());
			return true;
		} else return false;
	}
	
	public int sendOfflineGroupMessage(int touser,int groupid,String from,String msg) throws SQLException {
		String[] args= {"to_user_id","from_user","text","group_id"};
		Object[] vals= {Integer.valueOf(touser),from,msg,Integer.valueOf(groupid)};
		PreparedStatement ps=insertMaker("offline_group_messages", 4, args,vals);
		return ps.executeUpdate();
	}
	
	public ResultSet joinedGroups(int userid) throws SQLException {
		
		PreparedStatement ps=selectMaker("g.* , gb.id"," groups g, groups_backup gb ","gb.user_id",userid,"g.group_id = gb.group_id");
		return ps.executeQuery();
	}

	public int joinGroup(int userid,int groupid) throws SQLException {
		String[] args= {"group_id","user_id"};
		Object[] vals= {Integer.valueOf(groupid),Integer.valueOf(userid)};
		PreparedStatement ps=insertMaker("groups_backup", 2, args,vals);
		return ps.executeUpdate();
	}
	
	public int leaveGroup(int userid,int groupid) throws SQLException {
		
		PreparedStatement ps=this.deleteMaker("groups_backup", "user_id", userid, "group_id = "+String.valueOf(groupid)) ;
		
		return ps.executeUpdate();
	}

	public int createGroup(String groupname) throws SQLException {
		String[] args= {"groupname"};
		String[] vals= {groupname};
		PreparedStatement ps=insertMaker("groups", 1, args,vals);
		ps.executeUpdate();
		ResultSet rs=ps.getGeneratedKeys();
		if(rs.next()) {
			return rs.getInt(1);
		} else return -1;
	}
	
	public Object[] getRow(ResultSet rs) throws SQLException {
		Object[] results= new Object[rs.getMetaData().getColumnCount()];
		for(int i=0;i<rs.getMetaData().getColumnCount();i++) results[i]=rs.getObject(i+1);
		return results;
	}
	
	public Message[] getMessages(int uid) {
		ResultSet off,offg;
		ArrayList<Message> all=new ArrayList<Message>();
		try { 
			off=this.readUserLoggedMessages(uid);
			while(off.next()) {
				off.getString(0);
				//all.add(new Message());
			}
			offg=this.readUserLoggedMessages(uid);
			while(offg.next()) {
				
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return (Message[]) all.toArray();
	}
	
	public static void main(String[] args){
		
		ChatServerDataBase db=new ChatServerDataBase();
		try {
			db.connect("localhost", "test", "root", "");
			//db.addUser("sroshi", "password");
			System.out.println(db.loginAuthenticator("sroshi", "password"));
			//System.out.println(db.sendOfflineMessage(1, "srosh", "HELLLLO"));
			//System.out.println(db.sendOfflineGroupMessage(3, 1, "srosh", "HELLLLO"));
			//ResultSet rs= db.readUserLoggedMessages(1);
			//ResultSet rs= db.readGroupLoggedMessages(3);
			//System.out.println(db.joinGroup(1, 1));
			//System.out.println(db.createGroup("DOO"));

			System.out.println(db.createGroup("YOUOUOU"));
			ResultSet rs=db.joinedGroups(1);
			
			while(rs.next()) {
				Object[] r=db.getRow(rs);
				System.out.println(r.length);
				for(int i=0;i<rs.getMetaData().getColumnCount();i++)
				System.out.println(rs.getMetaData().getColumnName(i+1)+" : "+ r[i]);
			}
			//System.out.println(ps.toString());
			
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}


}
