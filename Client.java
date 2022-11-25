import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.*;
import java.net.*;


public class Client {

    Socket socket;
    String serverIP;
    String chatRoom;
    int chatPort, filePort;
    boolean successFlag = false;

    clientFileSender clientFileSender;
    clientFileReceiver clientFileReceiver;

    /* 클라이언트의 정보 저장 */
    public Client(String serverIP, int chatPort, int filePort) {
        this.serverIP = serverIP;
        this.chatPort = chatPort;
        this.filePort = filePort;
    }

    public void start() {
        /* 서버와 TCP 연결 시도 */
        try{
            socket = new Socket(serverIP, chatPort);
        } catch(IOException e) {
            e.printStackTrace();
        }

        /* 채팅방에 성공적으로 입장할 때까지 반복 */
        while(successFlag != true) {
            /* 채팅방 접속을 위한 대기방에 접속한다 */
            watingRoomSender clientSender = new watingRoomSender(this);
            clientSender.start();
            watingRoomReceiver clientReceiver = new watingRoomReceiver(this);
            clientReceiver.start();

            /* 수신 대기방 클라이언트가 종료될 때까지 대기 */
            try {
                clientReceiver.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /* 채팅방 입장 */
        Runnable r = new chatRoom(this);
        Thread chatRoom = new Thread(r);
        chatRoom.start();
        senderChatRoom senderChatRoom = new senderChatRoom(this, (chatRoom)r);
        senderChatRoom.start();

    }


    public static void main(String[] args) {

        /* 실행인자가 format에 맞는지 확인한다. */
        if(args.length != 3 || !args[1].chars().allMatch(Character::isDigit) || !args[2].chars().allMatch(Character::isDigit)){
            
            System.out.printf("%s\n%41s\n%s\n\n%35s\n%50s\n\n%s\n",
            "==================================================================",
            "Execution Error",
            "==================================================================",
            "프로그램 실행 인자를 제대로 입력했는지 확인해주세요!",
            "입력 예시: java Client localhost 2020 2021",
            "==================================================================");

            System.exit(0);
        }


        /* 클라이언트 구동 */
        Client client = new Client(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        client.start();
    }
}

/* '대기방 -> '서버' 메세지를 전송하는 스레드 */
class watingRoomSender extends Thread{
    Client client;
    Socket socket;

    public watingRoomSender(Client client){
        this.client = client;
        this.socket = client.socket;
    }

    public void run() {
		try {
			PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in)); 

            while (true) {
				String msg = reader.readLine();
				String[] userInput = msg.split("\\s");

                writer.println(msg); // 서버에 명령어 전송

				/* 1. '#JOIN' 커맨드인 경우 :  */
                if(userInput[0].equals("#JOIN") && userInput.length == 3){
                    client.chatRoom = userInput[1];
                    break;
                }
                
                /* 2. '#CREATE' 커맨드인 경우 : 서버에 CREATE 리퀘스트를 보낸다 */
                else if(userInput[0].equals("#CREATE") && userInput.length == 3){
                    client.chatRoom = userInput[1];
                    break;
                }

                /* 3. '#EXIT' 커맨드인 경우 : 프로그램을 종료한다 */
                if(msg.equals("#EXIT")){
                    System.out.println("===================================================================");
					System.out.println("     채팅 프로그램을 종료합니다");
					System.out.println("===================================================================");
                    
                    System.exit(0); //프로그램 종료
                }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

/* '서버' -> '대기방' 메세지를 받는 스레드 */
class watingRoomReceiver extends Thread{
    Client client;
    Socket socket;
    
    public watingRoomReceiver (Client client){
        this.client = client;
        this.socket = client.socket;
    }

    @Override
    public void run() {
		try {
            InputStream input = socket.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			
            while (true) {	
                String str = reader.readLine();
                if(!str.equals("") || str.length() != 0){
                    /* 1. 채팅방 생성 가능하다고 응답받은 경우 : flag을 true로 바꾸어 chatroom 스레드를 활성화 한다 */
                    if(str.equals("#SYSTEM_CREATE") || str.equals("#SYSTEM_JOIN")){
                        client.successFlag = true;
                        break;
                    }
                    /* 2. 채팅방 생성 가능하다고 응답받은 경우 : flag을 false로 냅두고 에러 메세지를 출력한다 */
                    else if(str.equals("#SYSTEM_CREATE_FAILURE") || str.equals("#SYSTEM_JOIN_FAILURE")){
                        break;
                    }
                    /* 3. 그 외 명령어에 대한 처리가 아닌 경우 : 그대로 출력한다 */
                    else{
                        System.out.println(str);
                    }
                }
			}
		} catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
		}
	}
}

/* '채팅방'에서 cmd에서 텍스트를 입력해도 통신할 수 있도록 하는 스레드 */
class senderChatRoom extends Thread {
    Client client;
    chatRoom chatRoom;
    Socket msgSocket;

    public senderChatRoom(Client client, chatRoom chatRoom){
        this.client = client;
        this.chatRoom = chatRoom;
        this.msgSocket = client.socket;
    }

    public void run() {
		try {
			PrintWriter msgWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(msgSocket.getOutputStream())),true);
			BufferedReader msgReader = new BufferedReader(new InputStreamReader(System.in)); 

            while (true) {
				String msg = msgReader.readLine();
				String[] userInput = msg.split("\\s");

                if(msg.equals("#EXIT")){
                    msgWriter.println(msg);
                    msgSocket.close(); // 소켓 종료
                    System.exit(0); // 프로세스 종료
                }
                else if(userInput[0].equals("#PUT") && userInput.length == 2){
                    msgWriter.println(msg);
                    chatRoom.fileName = userInput[1];

                }
                else if(userInput[0].equals("#GET") && userInput.length == 2){
                    msgWriter.println(msg);
                    chatRoom.fileName = userInput[1];
                }
                else if(msg.equals("#STATUS")){
                    msgWriter.println(msg);
                }
                else{
                    msgWriter.println(msg);
                }
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

/* '채팅방' 스레드 */
class chatRoom extends JFrame implements ActionListener, Runnable{
    
    // GUI를 위한 선언
    JPanel panel = new JPanel();
    JTextArea chatArea = new JTextArea(); // 채팅창 
    JScrollPane scrollPane = new JScrollPane(); // 채팅창 스크롤
    JTextField inputText = new JTextField(); // 유저 입력칸
    JLabel roomLabel = new JLabel(); // 채팅방 이름

    String sectionLine = "---------------------------------------";

    // 통신을 위한 선언
    Client client;
    Socket msgSocket;
    int filePort;
    String chatRoom, fileName;
    PrintWriter msgWriter, fileWriter;
    BufferedReader msgReader, fileReader;


    public chatRoom(Client client) {
        this.client = client;
        msgSocket = client.socket;
        this.filePort = client.filePort;
        this.chatRoom = client.chatRoom;

        /* GUI 기본 셋팅 */ 
        setTitle("채팅 프로그램");
        setSize(500,400);

        /* 채팅 화면 구성 */
        startGUI();
    }

    /* 채팅화면을 구성하고 윈도우 창에 띄우는 함수 */
    public void startGUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initGUI();
        inputText.addActionListener(this); // 유저의 입력칸을 파싱
    }

    /* 채팅화면을 구성하기 위한 함수 */
    public void initGUI() {
        BorderLayout borderLayout = new BorderLayout();
        setLayout(borderLayout);
        
        panel.setLayout(null); // 절대 위치로 선정
    
        chatArea.setEditable(false); // 채팅창 수정 불가능
        scrollPane = new JScrollPane(chatArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // 채팅 텍스트창의 스크롤 설정
        inputText.setBorder(new LineBorder(Color.LIGHT_GRAY, 1)); // 입력칸 디자인

        roomLabel.setText("| '" + chatRoom + "' 의 채팅방"); // 채팅방 이름 디자인
        roomLabel.setForeground(Color.darkGray);
        Font font = new Font("SanSerif", Font.BOLD, 25);
        roomLabel.setFont(font);

        scrollPane.setBounds(7, 40, 487, 293); // 각 컴포넌트 위치를 배치한다
        inputText.setBounds(7, 335, 488, 30);
        roomLabel.setBounds(7, 4, 487, 35);

        panel.add(roomLabel);
        panel.add(scrollPane);
        panel.add(inputText);

        add(panel);
        setVisible(true);
    }

    /* '서버' -> '채팅방' 메세지를 받는 함수 */
    @Override
    public void run() {
        try {
            InputStream input = msgSocket.getInputStream();
			msgReader = new BufferedReader(new InputStreamReader(input));

			while (true) {
				String str = msgReader.readLine();

                if((!str.equals("")) || str.length() != 0){
                    if(str.equals("#SYSTEM_EXIT")){ 
                        System.out.println(str);
                        chatArea.append(str + "\n");
                        break;
                    }
                    else if(str.equals("#SYSTEM_PUT")){ 
                        // 파일저장소 스레드 생성 및 실행
                        client.clientFileSender = new clientFileSender(this, client, fileName);
                        client.clientFileSender.start();
                    }
                    else if(str.equals("#SYSTEM_GET")){ 
                        // 파일저장소 스레드 생성 및 실행
                        client.clientFileReceiver = new clientFileReceiver(this, client, fileName);
                        client.clientFileReceiver.start();
                    }
                    else if(str.equals("#SYSTEM_STATUS")){
                        System.out.println(str);
                        chatArea.append(str + "\n");
                    }  
                    /* 시스템 메세지가 아닌 경우 */
                    else{
                        System.out.println(str);
                        chatArea.append(str + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength()); // 스크롤을 최하단으로 배치
                    }
                }
			}
		} catch (Exception e) {
			e.printStackTrace(); 
		}
    }

    /* '채팅방' -> '서버' 메세지를 전송하는 함수 */
    @Override
    public void actionPerformed(ActionEvent e) {
        try{
            msgWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(msgSocket.getOutputStream())),true);  

            String msg = inputText.getText(); //입력칸의 문자열 파싱하여 저장
			
            String[] userInput = msg.split("\\s");

			/* 1. '#EXIT' 커맨드인 경우 : 서버에게 로그아웃 메세지를 보낸 후, 클라이언트 종료 */
            if(msg.equals("#EXIT")){
                msgWriter.println(msg);
                msgSocket.close(); // 소켓 종료
                System.exit(0); // 프로세스 종료
            }
            /* 2. '#PUT' 커맨드인 경우 : 파일이름 저장 + 새로운 TCP 연결 + 파일 송신을 위한 스레드 실행 */
            else if(userInput[0].equals("#PUT") && userInput.length == 2){
                msgWriter.println(msg); // 서버에게 리퀘스트 요청 후, 응답을 기다린다
                fileName = userInput[1]; // 파일 이름 저장
            }
            /* 3. '#GET' 커맨드인 경우 : 파일이름 저장 + 새로운 TCP 연결 + 파일 수신을 위한 스레드 실행 */
            else if(userInput[0].equals("#GET") && userInput.length == 2){
                msgWriter.println(msg); // 서버에게 리퀘스트 요청 후, 응답을 기다린다
                fileName = userInput[1]; // 파일 이름 저장
            }
            /* 4. '#STATUS' 커맨드인 경우 : 서버에게 현재 채팅방 구성원을 알려달라는 리퀘스트 요청 */
            else if(msg.equals("#STATUS")){
                msgWriter.println(msg);
            }
            /* 5. 명령어가 아닌 경우 : 메세지 그대로 서버에게 전송하고, 서버에서 메세지를 처리하여 브로드캐스팅 */
            else{
                msgWriter.println(msg); 
            }
            inputText.setText(""); // 전송 후 입력칸을 비운다
        } catch (IOException e0) {
              e0.printStackTrace(); 
        }
    }
}

/* '파일 송신' 스레드 */
class clientFileSender extends Thread{

    Client client;
    Socket fileSocket;
    String fileName;
    PrintWriter writer;
    BufferedReader reader;
    chatRoom chatRoom;
    long fileSize;

    public clientFileSender(chatRoom thread, Client client, String fileName) {
        chatRoom = thread;
        this.client = client;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        File file = new File("./" + fileName);
        
        /* 파일이 존재하지 않는 경우 : 에러 메세지 출력 */
        if (!file.exists()) {
            /* cmd창 출력부분 */
            System.out.println("---------------------------------------");
            System.out.println("'" + fileName + "' 해당 파일이 없습니다.");
			System.out.println("작업을 중단 합니다...");
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
            chatRoom.chatArea.append("'" + fileName + "' 해당 파일이 없습니다.\n");
            chatRoom.chatArea.append("작업을 중단 합니다...\n"); 
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
        }
            fileSize = file.length();

            /* cmd창 출력부분 */
            System.out.println("---------------------------------------");
            System.out.println("파일명: " + fileName);
            System.out.println("파일 크기(Bytes): " + fileSize);
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
            chatRoom.chatArea.append("파일명: " + fileName+ "\n");
            chatRoom.chatArea.append("파일 크기(Bytes): " + fileSize+ "\n");
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");

        try { 
            fileSocket = new Socket(client.serverIP, client.filePort); // TCP 커넥션
            /* TCP 연결 실패 */
            if(!fileSocket.isConnected()){ 
                /* cmd창 출력부분 */
                System.out.println("---------------------------------------");
                System.out.println("파일 전송 서버와의 연결에 실패하였습니다.");
                System.out.println("---------------------------------------");
                /* swing창 출력부분 */
                chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
                chatRoom.chatArea.append("파일 전송 서버와의 연결에 실패하였습니다.\n");
                chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
            }
            
			DataOutputStream dout = new DataOutputStream(fileSocket.getOutputStream());
			FileInputStream fin = new FileInputStream(file);
			byte[] buffer = new byte[1000]; //1000Byte = 1KByte 씩 전송
			int readByte = 0;
			long totalReadBytes = 0;
            
            dout.writeLong(file.length()); //서버에게 파일 사이즈 송신

            /* cmd창 출력부분 */
            System.out.println("---------------------------------------");
            System.out.println("해당 파일을 서버에 업로드하고 있습니다...");
            System.out.printf("%s", "파일 송신 진척도: ");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
            chatRoom.chatArea.append("해당 파일을 서버에 업로드하고 있습니다...\n파일 송신 진척도: ");

			/* 파일 내용 -> 소켓 전송 */
			while((readByte = fin.read(buffer)) != -1){
				dout.write(buffer, 0, readByte);
                totalReadBytes += readByte;

                /* 64KByte당 '#' 출력 */
                if((totalReadBytes/1000)%64 == 0 && totalReadBytes != 0) { 
                    System.out.printf("%s","#");
                    chatRoom.chatArea.append("#");
                }
                dout.flush();
			}

            /* cmd창 출력부분 */
			System.out.println("");
			System.out.println("파일 전송이 완료되었습니다.");
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append("\n파일 전송이 완료되었습니다.\n");
            chatRoom.chatArea.append(chatRoom.sectionLine);
		
			dout.close();
			fin.close();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/* '파일 수신' 스레드 */
class clientFileReceiver extends Thread{

    Client client;
    Socket fileSocket;
    String fileName;
    PrintWriter writer;
    BufferedReader reader;
    chatRoom chatRoom;
    long fileSize;

    public clientFileReceiver(chatRoom thread, Client client, String fileName) {
        this.chatRoom = thread;
        this.client = client;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        File file = new File("./server_data/" + fileName);
        
        /* 파일이 존재하지 않는 경우 : 에러 메세지 출력 */
        if (!file.exists()) {
            /* cmd창 출력부분 */
            System.out.println("---------------------------------------");
            System.out.println("'" + fileName + "' 해당 파일이 없습니다.");
			System.out.println("작업을 중단 합니다...");
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine);
            chatRoom.chatArea.append("\n'" + fileName + "' 해당 파일이 없습니다.");
            chatRoom.chatArea.append("\n작업을 중단 합니다..."); 
            chatRoom.chatArea.append(chatRoom.sectionLine);
        }
            fileSize = file.length();

            /* cmd창 출력부분 */
            System.out.println("파일명: " + fileName);
            System.out.println("파일 크기(Bytes): " + fileSize);
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine + "\n");
            chatRoom.chatArea.append("파일명: " + fileName + "\n");
            chatRoom.chatArea.append("파일 크기(Bytes): " + fileSize + "\n");
            chatRoom.chatArea.append(chatRoom.sectionLine + "\n");
        
        try { 
            fileSocket = new Socket(client.serverIP, client.filePort); // TCP 커넥션
            /* TCP 연결 실패 */
            if(!fileSocket.isConnected()){ 
                System.out.println("파일 전송 서버와의 연결에 실패하였습니다.");
                chatRoom.chatArea.append("파일 전송 서버와의 연결에 실패하였습니다.\n");
            }

            DataInputStream din = new DataInputStream(fileSocket.getInputStream());
            long fileSize = din.readLong(); // 서버로부터 파일사이즈 수신
            String dic = din.readUTF(); // 서버로부터 저장할 폴더명(유저이름) 수신
            
            String savePath = "./" + dic;
            new File(savePath).mkdir();
			File saveFile = new File(savePath, fileName); // ./'유저명' 경로에 파일을 저장

            byte[] buffer = new byte[1000]; //1000Byte = 1KByte 씩 수신
			int readByte = 0;
            long totalReadBytes = 0;

            /* cmd창 출력부분 */
            System.out.println("---------------------------------------");
            System.out.println("해당 파일을 서버에서 다운받고 있습니다...");
            System.out.printf("%s", "파일 수신 진척도: ");
            /* swing창 출력부분 */
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");
            chatRoom.chatArea.append("해당 파일을 서버에서 다운받고 있습니다...\n파일 수신 진척도: ");

            /* 소켓 내용 -> 파일로 작성 */
            FileOutputStream fout = new FileOutputStream(saveFile);
            while((readByte = din.read(buffer)) != -1){
				fout.write(buffer , 0, readByte);
                totalReadBytes += readByte;

                /* 64KByte당 '#' 출력 */
                if((totalReadBytes/1000)%64 == 0 && totalReadBytes != 0) { 
                    System.out.printf("%s","#");
                    chatRoom.chatArea.append("#");
                }
                /* 파일 사이즈만큼 수신했을 때, 수신 중단 */
                if(totalReadBytes == fileSize) 
                    break;
			}
            /* cmd창 출력부분 */
            System.out.println("");
			System.out.println("파일 수신이 완료되었습니다. / 수신경로: " + savePath + fileName);
            System.out.println("---------------------------------------");
            /* swing창 출력부분 */
            chatRoom.chatArea.append("\n파일 수신이 완료되었습니다. / 수신경로: " + savePath + fileName + "\n");
            chatRoom.chatArea.append(chatRoom.sectionLine+ "\n");

            din.close();
			fout.close();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
