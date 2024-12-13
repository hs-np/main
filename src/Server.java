import javax.swing.*;
import java.awt.*;
import java.awt.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server extends JFrame{

    private JTextArea server_display;
    private String address;
    private String port;
    private JButton exit, disconnect, connect;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Vector<User> users = new Vector<>();
    private Vector<User> waitingRoom = new Vector<>();
    //유저가 매칭버튼을 누르면 매칭방에서 대기하도록 함.
    private Vector<User[]> gameRoom = new Vector<>();
    //매칭방에 두명 이상 존재하면 두명 씩 게임방으로 초대. User[] 한 게임방에 두명씩 정보를 추가해야 되기 때문.

    private HashMap<String,String> userLoginData = new HashMap<>(Map.of(
            "root","1234",
            "root1","4321"
    ));
    //회원관리를 위한 해시맵 컬렉션: 아이디와 비밀번호를 key, value 값으로 설정하여 로그인 확인 시 빠르게 찾을 수 있어 적절하다고 판단.

    private ObjectInputStream ois = null;
    private BufferedWriter br = null;
    ObservableDeque<String> deque = new ObservableDeque<>();
    DequeLogger<String> logger = new DequeLogger<>();

    // 옵저버 설정

    public Server(String address, String port){
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setBounds(800, 300, 500, 500);
        this.setTitle("ObjectInputServer GUI");
        this.port = port;
        deque.setObserver(logger);
        ServerGUI();
        this.setVisible(true);

    }
    public void ServerGUI(){
        this.getContentPane().add(createDisplayPanel(), BorderLayout.CENTER);
        this.getContentPane().add(createControlPane(), BorderLayout.SOUTH);
    }
    public JPanel createDisplayPanel(){

        JPanel DisplayPanel = new JPanel(new BorderLayout());

        server_display = new JTextArea();
        server_display.setEditable(false);

        DisplayPanel.add(server_display, BorderLayout.CENTER);
        DisplayPanel.add(new JScrollPane(server_display));

        return DisplayPanel;

    }
    public JPanel createControlPane(){
        JPanel controlPanel = new JPanel(new GridLayout(0, 1));
        connect = new JButton("서버시작");
        disconnect = new JButton("서버중단");

        controlPanel.add(connect);
        controlPanel.add(disconnect);
        connect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        startServer();
                    }
                });
                acceptThread.start();
            }
        });
        disconnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnect();
            }
        });

        return controlPanel;
    }
    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            server_display.append("서버가 시작되었습니다\n");

            while (true) {
                Socket cs = serverSocket.accept();
                 ois = new ObjectInputStream(cs.getInputStream());
                 br = new BufferedWriter(new OutputStreamWriter(cs.getOutputStream()));

                    LoginData data = (LoginData) ois.readObject();
                    server_display.append("로그인 전.\n");
                    SignupSigninProcess(cs, data);
            }
        } catch (IOException e) {
            server_display.append("startServer 오류 발생\n");
        }
        catch (ClassNotFoundException e) {
            server_display.append("데이터 읽기 오류\n");
        }
        finally{
            try{
                ois.close();
                br.close();
            }catch(IOException e){
                System.out.println(e.getMessage());
            }
        }
    }
    public void SignupSigninProcess(Socket cs, LoginData data) throws IOException{
        String loginId = data.getId();
        String loginPw = data.getPw();
        String mode = data.getMode();
        switch(mode){
            case "로그인":
                if (userLoginData.containsKey(loginId) && userLoginData.get(loginId).equals(loginPw)) {
                    br.write("로그인수락\n");
                    br.flush();
                    server_display.append("로그인수락.\n");
                    User user = new User(cs, loginId);
                    users.add(user);
                    user.start();
                } else {
                    server_display.append("로그인 실패.\n");
                    br.write("로그인실패\n");
                    br.flush();
                }
                break;
            case "회원가입":
                if(!userLoginData.containsKey(loginId)){
                    server_display.append("회원가입 성공\n");
                    userLoginData.put(loginId, loginPw);
                    br.write("회원가입성공\n");
                    br.flush();
                }
                else{
                    br.write("회원가입실패\n");
                    br.flush();
                }
                break;
        }
    }
    public void disconnect() {
        try {
            serverSocket.close();
        }
        catch(IOException e){
            server_display.append("서버 종료\n");
        }
    }

    class User extends Thread{
        private Socket socket;
        private Writer chatSender;
        private Reader testReader;
        private String loginId;

        public User(Socket socket, String loginId){
            this.socket =socket;
            this.loginId = loginId;
        }
        @Override
        public void run(){
            chatSend(socket);
        }

        //클라이언트 상태 전달 및 게임 진행 중계.
        private void broadCast(String msg){
            for (User u : users) {
                u.sendMessage(msg);
            }
        }

        private void chatSend(Socket cs){
            //chatSender = null;

            try {
                chatSender = new BufferedWriter(new OutputStreamWriter(cs.getOutputStream()));
                testReader = new BufferedReader(new InputStreamReader(cs.getInputStream()));
                chatSender.write("서버 연결 성공\n");
                chatSender.flush();

                String msg;
                while((msg = ((BufferedReader)testReader).readLine()) != null){
                    processMessage(msg);
                }

            }
            catch(IOException e){
                e.printStackTrace();
            }
            finally {
                cleanupResources(cs);
            }
        }

        private void processMessage(String msg) {
            switch (msg) {
                case "매칭":
                    handleMatching();
                    break;
                case "매칭취소":
                    handleMatchingCancel();
                    break;
                case "게임종료":
                    handleGameEnd();
                    break;
                default:
                    deque.add(msg);
                    broadCast(msg);
                    break;
            }
        }
        // 매칭 처리
        private void handleMatching() {
            if (waitingRoom.isEmpty()) {
                broadCast(this.loginId + "님이 대기방에 입장했습니다.\n");
                server_display.append(this.loginId + "님이 대기방에 입장했습니다.\n");
                waitingRoom.add(this);
                System.out.println(waitingRoom);
            } else {
                User matchedUser = waitingRoom.remove(0);
                User[] userMatching = {matchedUser, this};
                broadCast(matchedUser.loginId + "과 " + this.loginId + "님이 게임을 시작했습니다.\n");
                gameRoom.add(userMatching);
                System.out.println(gameRoom);

                matchedUser.sendMessage("게임시작");
                this.sendMessage("게임시작");
            }
        }
        // 매칭 취소 처리
        private void handleMatchingCancel() {
            synchronized (waitingRoom) {
                waitingRoom.removeIf(user -> user == this);
                broadCast(this.loginId + "님이 매칭을 종료했습니다.");
            }
        }
        // 게임 종료 처리
        private void handleGameEnd() {
            server_display.append(this.loginId + "님의 게임 중 탈주를 확인했습니다.\n");
            synchronized (gameRoom) {
                gameRoom.removeIf(room -> room[0] == this || room[1] == this);
                broadCast(this.loginId + "님이 게임에서 나갔습니다. 게임 방이 해체되었습니다.");
            }
        }
        // 리소스 정리
        private void cleanupResources(Socket cs) {
            try {
                if (chatSender != null) chatSender.close();
                if (testReader != null) testReader.close();
                if (cs != null && !cs.isClosed()) cs.close();

                synchronized (waitingRoom) {
                    waitingRoom.removeIf(user -> user == this);
                }
                synchronized (gameRoom) {
                    gameRoom.removeIf(room -> room[0] == this || room[1] == this);
                }

                server_display.append(this.loginId + "님의 클라이언트와의 연결이 종료되었습니다.\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendMessage(String msg){
            try{
                chatSender.write(msg+"\n");
                chatSender.flush();
            }
            catch(IOException e){

            }
        }
    }

    public static void main(String[] args){
        String address = "localhost";
        String port = "1234";
        new Server(address , port);
    }

    // Observer 인터페이스 정의
    interface DequeObserver<E> {
        void onElementAdded(E element);
        void onElementRemoved(E element);
    }

    // ObservableDeque 클래스
    class ObservableDeque<E> {
        private final Deque<E> deque = new ArrayDeque<>();
        private DequeObserver<E> observer; // 단일 옵저버

        // 옵저버 등록
        public void setObserver(DequeObserver<E> observer) {
            this.observer = observer;
        }

        // 요소 추가
        public void add(E element) {
            deque.add(element);
            if (observer != null) {
                observer.onElementAdded(element);
            }
        }

        // 요소 제거
        public E remove() {
            E removed = deque.remove();
            if (observer != null) {
                observer.onElementRemoved(removed);
            }
            return removed;
        }

        // 기타 Deque 메서드 위임 (필요 시 추가 가능)
        public boolean isEmpty() {
            return deque.isEmpty();
        }

        public E peek() {
            return deque.peek();
        }
    }

    // 예제: 옵저버 구현
    class DequeLogger<E> implements DequeObserver<E> {
        @Override
        public void onElementAdded(E element) {
            System.out.println("Element added: " + element);
        }

        @Override
        public void onElementRemoved(E element) {
            System.out.println("Element removed: " + element);
        }
    }
}
