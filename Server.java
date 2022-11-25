import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.*;

public class Server {

    Map<User, chatRoomThread> connectedClientList; // '채팅방'에 연결된 클라이언트 리스트
	List<String> chatRoomList; // 채팅방 스레드 객체
    List<File> fileList; // 수신받은 파일 DB

    serverFileReceiver serverFileReceiver;
    serverFileSender serverFileSender;

    int chatPort, filePort;
    Socket clientSocket;
    static Thread watingRoom;
    Thread fileStorageRoom;
    
    /* 클라이언트(유저) 정보 */
    User targetUser;  
	public static class User{
		String userName = "";
		String chatRoom = "";
	}

    public Server(int chatP, int fileP){
        connectedClientList = new HashMap<User, chatRoomThread>();
        chatRoomList = new ArrayList<String>();
        fileList = new ArrayList<File>();
        chatPort = chatP;
        filePort = fileP;
    }
    
    public synchronized void addFileList(File file){
        fileList.add(file);
        System.out.println("[SYSTEM] '" + file.getName() + "' 파일이 성공적으로 저장 되었습니다.");
        System.out.println(fileList.size());
    }

    public synchronized void addConnectedUserList(User user, chatRoomThread thread){
        connectedClientList.put(user, thread);
        System.out.println("[SYSTEM] " + user.userName + "님이 (" + user.chatRoom + ")방에 로그인 하였습니다.");
    }

    public synchronized void removeConnectedUserList(User user, Thread thread){
        connectedClientList.remove(user, thread);
        System.out.println("[SYSTEM] " + user.userName + "님이 (" + user.chatRoom + ")방에서 로그아웃 하였습니다.");
    }

    public synchronized void sendMsgToClient(String msg, User targetUser){
        for(Server.User user : connectedClientList.keySet()){
            if(user.chatRoom.equals(targetUser.chatRoom)){
                connectedClientList.get(user).sender(msg);
            }
        }
    }

    public void chatRoomStart(){
        /* 채팅방 스레드 생성 및 접속 */
        chatRoomThread chatRoomThread = new chatRoomThread(this, clientSocket, targetUser);
        chatRoomThread.start();
    }

    public void watingRoomStart(){
        try {
            try (ServerSocket serverSocket = new ServerSocket(chatPort)) {
                System.out.println("[SYSTEM] 대기방 서버가 성공적으로 가동되었습니다.");

                while(true){

                    clientSocket = serverSocket.accept(); //TCP connection 완료
                    System.out.println("[SYSTEM] 새 클라이언트 연결 (IP: " + clientSocket.getLocalAddress() + ", Port: " + clientSocket.getLocalPort() + ")");
                    
                    targetUser = new User();
                    /* 대기방 스레드 생성 및 접속 */
                    watingRoom = new watingRoomThread(this, clientSocket);
                    // 여기서 성공적으로 채팅방에 접속하게 되면 this.user 정보에 저장하고, 이 정보를 토대로 채팅방으로 입장하게 함
                    watingRoom.start(); 
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        /* 실행인자가 format에 맞는지 확인한다. */
        if(args.length != 2 || !args[0].chars().allMatch(Character::isDigit) || !args[1].chars().allMatch(Character::isDigit)){
			
			System.out.printf("%s\n%41s\n%s\n\n%35s\n%44s\n\n%s\n",
			"==================================================================",
			"Execution Error",
			"==================================================================",
			"프로그램 실행 인자를 제대로 입력했는지 확인해주세요!",
			"입력 예시: java Server 2020 2021",
			"==================================================================");

            System.exit(0);
        }

        /* 대기방 서버 구동 -> 채팅방 서버 구동 */
        Server server = new Server(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.watingRoomStart();
    }
    
}


/* 채팅방 스레드 */
class chatRoomThread extends Thread {

    Socket socket;
    Server server;
    Server.User user;
    
    PrintWriter writer; //송신용
    BufferedReader reader; //수신용

    String sectionLine = "---------------------------------------";

    public chatRoomThread(Server server, Socket socket, Server.User user) {
        this.server = server;
        this.socket = socket;
        this.user = user;

        server.addConnectedUserList(user, this); // 채팅방에 연결된 클라이언트 리스트에 해당 클라이언트를 추가
        for(Server.User u : server.connectedClientList.keySet()){
            System.out.println("[SYSTEM] 채팅방에 새 클라이언트 연결 (이름: " + u.userName + " / 채팅방: " + u.chatRoom + ")");
        }
	}

    public void sender(String msg){
        writer.println(msg);
    }

    public void loginMsg(){
        server.sendMsgToClient(sectionLine, user);
        server.sendMsgToClient("     [" + user.userName + "] 님이 로그인 하셨습니다.", user);
        server.sendMsgToClient(sectionLine, user);
    }

    public void statusMsg(){
        sender(sectionLine);
        sender("     채팅방 정보");
        sender(sectionLine);

        int i = 0;
        for(Server.User u : server.connectedClientList.keySet()){
            if(u.chatRoom.equals(this.user.chatRoom)){
                i++;
                sender("     " + i + ". " + u.userName);
            }
        }
        sender(sectionLine);
        sender("채팅방 이름은 '" + this.user.chatRoom + "' 이며, 현재 참여 중인 인원은 총 '" + i + "'명 입니다.");
        sender(sectionLine);
    }

    public void logoutMsg(){
        server.sendMsgToClient(sectionLine, user);
        server.sendMsgToClient("      [" + user.userName + "] 님이 로그아웃 하셨습니다.", user);
        server.sendMsgToClient(sectionLine, user);
    }

    public void run() {
		try{
            OutputStream out = socket.getOutputStream();
            InputStream input = socket.getInputStream();

            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out)),true);
			reader = new BufferedReader(new InputStreamReader(input));

            loginMsg(); // 클라이언트 로그인 메세지 출력

            // 클라이언트의 메세지 입력 대기
            while (true) {	
				String msg = reader.readLine();
                
                if((!msg.equals("")) || msg.length() != 0){
					String[] userInput = msg.split("\\s");

					/* 1. #EXIT 커맨드인 경우 : 채팅방에서 나가고, DB에서 유저 정보를 삭제 */
					if (msg.equals("#EXIT")){
						logoutMsg(); // 로그아웃 메세지 출력
			            server.removeConnectedUserList(user, this); // 해당 유저 정보 삭제 
						break;
					}
					/* 2. #STATUS 커맨드인 경우
						: (채팅방 이름)과 (구성원의 정보) 출력 */
					else if (msg.equals("#STATUS")){
						statusMsg();
					}
					/* 3. #PUT 커맨드인 경우 : */
					else if(userInput[0].equals("#PUT") && userInput.length == 2){
                        server.serverFileReceiver = new serverFileReceiver(this, server, userInput[1]);
                        server.serverFileReceiver.start();

                        writer.println("#SYSTEM_PUT"); // 성공적으로 스레드를 열었다고 응답
					}
					/* 4. #GET 커맨드인 경우 : */
					else if(userInput[0].equals("#GET") && userInput.length == 2){
                        server.serverFileSender = new serverFileSender(this, server, userInput[1]);
                        server.serverFileSender.start();

                        writer.println("#SYSTEM_GET"); // 성공적으로 스레드를 열었다고 응답
					}
					/* 5. 맨 앞글자가 '#'이 아닌 경우 : 정상 메세지로 인식하고, 채팅방에 있는 클라이언트들에게 메세지를 전송 */
					else if(msg.charAt(0) != '#'){
						String str = "FROM " + user.userName + ": " + msg;
						server.sendMsgToClient(str, user);
					}
				}
            }
        }catch(IOException e){
			logoutMsg(); // 로그아웃 메세지 출력
			server.removeConnectedUserList(user, this);
        } catch(NullPointerException e){
            logoutMsg(); // 로그아웃 메세지 출력
			server.removeConnectedUserList(user, this);
        }
    }
				
}

/* 대기방 스레드 */
class watingRoomThread extends Thread {

    Server server;
    Socket socket;
    BufferedReader reader; //입력
    PrintWriter writer; //출력
    
    Server.User user = new Server.User();

    public watingRoomThread(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
	}

    public void sendWelcomeMsg(){
        writer.printf("%s\n\n%5s%s\n%5s%s\n\n%s\n",
                "==================================================================",
                "","어서오세요! 오픈 채팅프로그램을 위한 대기방입니다.",
                "","이용하실 [채팅방의 이름]과 [사용자 이름]을 입력해주세요.",
                "==================================================================" );
    }

    public void sendCommandMsg(){
        writer.printf("%s\n%33s\n%s\n\n%13s%5s%9s%7s%7s%-13s\n%13s%5s%9s%7s%7s%-13s\n%13s%5s%9s%7s%7s%-23s\n\n%s\n",
        "==================================================================",
        "명령어 가이드",
        "------------------------------------------------------------------",
        "", "1.", "#CREATE", "채팅방이름", "사용자이름", "",
        "", "2.", "#JOIN", "채팅방이름", "사용자이름", "",
        "", "3.", "#EXIT", "", "", "",
        "==================================================================");
    }

    /* 이미 채팅방이 존재하는지 검사하는 함수 */
	public static boolean isChatNameExist(Map<Server.User, chatRoomThread> map, Server.User targetUser){
        Set<Server.User> list = map.keySet();
		for(Server.User u : list){
			/* 채팅방이 존재하는 경우 : true 반환 */
			if(u.chatRoom.equals(targetUser.chatRoom))
				return true;
		}
		return false;
	}
    
    /* 같은 채팅방 내에 중복 아이디가 있는지 검사하는 함수 */
    public boolean isUserNameExist(Map<Server.User, chatRoomThread> map, Server.User targetUser){
        Set<Server.User> list = map.keySet();
        for(Server.User u : list){
			/* (같은 채팅방 && 같은 이름) 인 경우 : true 반환 */  
			if(u.chatRoom.equals(targetUser.chatRoom) && (u.userName.equals(targetUser.userName)))
				return true;
		}
		return false;
    }

    @Override
    public void run(){
        try{
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);  
 
            sendWelcomeMsg();
            sendCommandMsg();

            while(true){
                String msg = reader.readLine();
            
                if(!msg.equals("") || msg.length() != 0){
                    String[] userInput = msg.split("\\s");
                    /* 1. #CREATE 커맨드인 경우 : 채팅방 이름이 이미 존재하는지 검사한 뒤 채팅방을 생성한다. */
					if (userInput[0].equals("#CREATE") && userInput.length == 3){
                        System.out.println("[SYSTEM] 클라이언트 IP: " + server.clientSocket.getLocalAddress() + " 'CREATE' 명령 수행");

                        user.chatRoom = userInput[1]; 
                        user.userName = userInput[2]; // 클라이언트의 정보 저장

                        /* 1.1. DB에 저장된 채팅방이 없는 경우 : 무조건 채팅방 생성 */
						if(server.connectedClientList.isEmpty()) {
						
							writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n",
                            "==================================================================",
                            "", "[채팅방 생성 성공]",
                            "", "[", user.chatRoom, "] 채팅방과 연결중입니다...",
                            "==================================================================");

                            writer.println("#SYSTEM_CREATE"); // 클라이언트에게 채팅방 생성 성공 메세지 전송

                            server.targetUser.chatRoom = user.chatRoom;
                            server.targetUser.userName = user.userName; // 클라이언트의 정보를 서버에 저장
                            server.chatRoomStart(); // 채팅방 연결 
                            
							break;
						}
						else{
							/* 1.2. 채팅방 이름이 이미 존재하는 경우 : 에러 메세지 출력 */
							if(isChatNameExist(server.connectedClientList, user)){

                                writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n",
                                "==================================================================",
                                "", "[채팅방 생성 실패]",
                                "", "[", user.chatRoom, "] 은 이미 사용중인 채팅방 이름입니다.",
                                "==================================================================");
                                writer.println("#SYSTEM_CREATE_FAILURE"); // 클라이언트에게 채팅방 생성 실패 메세지 전송		
                              
                                System.out.println("[SYSTEM] " + userInput[2] + "님이 채팅방(" + userInput[1] +") 생성에 실패하였습니다.");
							}
							/* 1.3. 채팅방 이름이 채팅방 서버DB에 존재하지 않는 경우 : 새로운 채팅방 생성 */
							else if((!isChatNameExist(server.connectedClientList, user))){
						    
                                server.chatRoomList.add(user.chatRoom); // 채팅방 이름을 리스트에 저장
                                
                                writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n",
                                "==================================================================",
                                "", "[채팅방 생성 성공]",
                                "", "[", user.chatRoom, "] 채팅방과 연결중입니다...",
                                "==================================================================");

                                writer.println("#SYSTEM_CREATE"); // 클라이언트에게 채팅방 생성 성공 메세지 전송

                                server.targetUser.chatRoom = user.chatRoom;
                                server.targetUser.userName = user.userName; // 클라이언트의 정보를 서버에 저장
                                server.chatRoomStart(); // 채팅방 연결
								break;
							}
                        }
                    }
                    /* 2. #JOIN 커맨드인 경우 : 채팅방이 존재하는지 검사한 뒤 채팅방에 가입한다.*/
					else if (userInput[0].equals("#JOIN") && userInput.length == 3){

                        System.out.println("[SYSTEM] 클라이언트 IP: " + server.clientSocket.getLocalAddress() + " 'JOIN' 명령 수행");

                        String chatRoom = userInput[1]; 
                        String userName = userInput[2]; // 클라이언트의 정보 저장

                        user.chatRoom=chatRoom; 
                        user.userName=userName; // 클라이언트의 정보를 서버에 저장

                        /* 2.1. 저장된 DB나 채팅방이 존재하지 않는 경우 : 에러 메세지 출력 */
						if(server.connectedClientList.isEmpty()){
							
                            writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n", 
                            "==================================================================", 
                            "", "[채팅방 접속 실패]"
                            ,"", "[", chatRoom, "] 은 존재하지 않는 채팅방입니다.",
                            "==================================================================");
                            writer.println("#SYSTEM_JOIN_FAILURE");
                            
                            System.out.println("[SYSTEM] " + userName + "님이 채팅방(" + chatRoom + ") 접속에 실패하였습니다.");
						}
						else{
							/* 2.2. 채팅방 존재 && 유저 이름 중복인 경우 : 에러 메세지 출력 */
							if(isUserNameExist(server.connectedClientList, user)){
								writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n", 
                                "==================================================================", 
                                "", "[채팅방 접속 실패]"
                                ,"", "[", userName, "] 은 이미 사용 중인 이름입니다.",
                                "==================================================================");
                                writer.println("#SYSTEM_JOIN_FAILURE");
                            
                                System.out.println("[SYSTEM] " + userName + "님이 채팅방(" + chatRoom + ") 접속에 실패하였습니다.");
						    }
							/* 2.3. 그 외 : 채팅방 연결 */
							else{

                                server.chatRoomList.add(chatRoom); // 채팅방 이름을 리스트에 저장
                                
                                writer.printf("%s\n\n%5s%s\n%5s%s%s%s\n\n%s\n",
                                "==================================================================",
                                "", "[채팅방 접속 성공]",
                                "", "[", chatRoom, "] 채팅방과 연결중입니다...",
                                "==================================================================");

								writer.println("#SYSTEM_JOIN");

                                server.targetUser.chatRoom = user.chatRoom;
                                server.targetUser.userName = user.userName; // 클라이언트의 정보를 서버에 저장
                                server.chatRoomStart(); // 채팅방 연결
								break;
							}
                        }
                    }

                    /* 3. 그 외, 잘못된 명령어를 입력한 경우 : 명령어 안내 메세지를 띄운다. */
					else{
                        sendCommandMsg();
                    }
                }   
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 

/* 파일 수신 스레드 */
class serverFileReceiver extends Thread{

    Server server;
    Socket fileSocket;
    String fileName;
    PrintWriter writer;
    BufferedReader reader;
    chatRoomThread chatRoom;
    long fileSize;

    public serverFileReceiver(chatRoomThread thread, Server server, String fileName) {
        this.chatRoom = thread;
        this.server = server;
        this.fileName = fileName;
    }
    
    public void run() {
        try {
            ServerSocket fileServer = new ServerSocket(server.filePort); 
            
            System.out.println("[SYSTEM] 파일저장소 서버가 성공적으로 가동되었습니다.");
            fileSocket = fileServer.accept();  // 새로운 소켓 연결 대기

            String savePath = "./server_data";
            new File(savePath).mkdir();
			File saveFile = new File(savePath, fileName); // ./server_dat 경로에 파일을 저장

			byte[] buffer = new byte[1000]; //1000Byte = 1KByte 씩 전송
			int readByte = 0;
            long totalReadBytes = 0;

            DataInputStream din = new DataInputStream(fileSocket.getInputStream());
	        FileOutputStream fout = new FileOutputStream(saveFile);
            fileSize = din.readLong(); // 클라이언트에게서 파일사이즈 수신

            System.out.println("[SYSTEM] 클라이언트(IP: " + fileSocket.getLocalAddress() + ", Port: " + fileSocket.getLocalPort() + ")에서 '" + fileName + "' 파일을 다운로드 합니다.");

			while((readByte = din.read(buffer)) != -1){
				fout.write(buffer , 0, readByte);
                totalReadBytes += readByte;

                /* 파일 사이즈만큼 수신했을 때, 수신 중단 */
                if(totalReadBytes == fileSize) 
                    break;
			}

			System.out.println("[SYSTEM] 파일이 성공적으로 저장되었습니다.");
            server.addFileList(new File(fileName)); // 파일저장소 리스트에 해당 파일을 추가

            din.close();
            fout.close();
            fileSocket.close();
            fileServer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/* 파일 송신 스레드 */
class serverFileSender extends Thread{

    Server server;
    Socket fileSocket;
    String fileName;
    PrintWriter writer;
    BufferedReader reader;
    chatRoomThread chatRoom;
    long fileSize;

    public serverFileSender(chatRoomThread thread, Server server, String fileName) {
        chatRoom = thread;
        this.server = server;
        this.fileName = fileName;
    }
    
    public void run() {
        try {
            File file = new File("./server_data/" + fileName);

            ServerSocket fileServer = new ServerSocket(server.filePort); 
            
            System.out.println("[SYSTEM] 파일저장소 서버가 성공적으로 가동되었습니다.");
            fileSocket = fileServer.accept();  // 새로운 소켓 연결 대기

            DataOutputStream dout = new DataOutputStream(fileSocket.getOutputStream());
			FileInputStream fin = new FileInputStream(file);

			byte[] buffer = new byte[1000]; //1000Byte = 1KByte 씩 전송
			int readByte = 0;

            dout.writeLong(file.length()); //서버에게 파일 사이즈 송신
            dout.writeUTF(chatRoom.user.userName);

            System.out.println("해당 파일을 클라이언트에 전송하고 있습니다...");

           /* 파일 내용 -> 소켓 전송 */
			while((readByte = fin.read(buffer)) != -1){
				dout.write(buffer, 0, readByte);
                dout.flush();
			}

            System.out.println("");
			System.out.println("파일 전송이 완료되었습니다.");

            dout.close();
            fin.close();
            fileSocket.close();
            fileServer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
