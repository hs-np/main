import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class Server extends JFrame{

    private JTextArea server_display;
    private String address;
    private String port;
    private JButton disconnect, connect;
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
    ObservableDeque deque = new ObservableDeque();
    DequeLogger logger = new DequeLogger();

    private ObservableDeque player1 = new ObservableDeque();
    private ObservableDeque player2 = new ObservableDeque();
    private String player1Name = "";
    private String player2Name = "";
    private String rectPoint = "";
    private int player1RectNum = 0;
    private int player2RectNum = 0;
    private List<String> list = new ArrayList<>();
    private boolean player1reStart = false;
    private boolean player2reStart = false;

    public Server(String address, String port){
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setBounds(800, 300, 500, 500);
        this.setTitle("DotAndBoxServer");
        this.port = port;
        player1.addObserver(logger);
        player2.addObserver(logger);
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

        private void chatSend(Socket cs){

            try {
                chatSender = new BufferedWriter(new OutputStreamWriter(cs.getOutputStream()));
                testReader = new BufferedReader(new InputStreamReader(cs.getInputStream()));
                chatSender.write("서버 연결 성공\n");
                chatSender.flush();

                String msg;
                while((msg = ((BufferedReader)testReader).readLine()) != null){
                    processMessage(msg);
                    SwingUtilities.invokeLater(() -> {
                            String tempPoint = rectPoint;
                            if (!tempPoint.equals("")) {
                                broadCast(loginId + ":" + "사각형:" + tempPoint);
                                if (loginId.equals(player1Name)) {
                                    player1RectNum++;
                                } else {
                                    player2RectNum++;
                                }
                                if (player1RectNum == 1 || player2RectNum == 1) {
                                    broadCast("게임종료");
                                }
                            }
                    });
                    //processMessage와 별개로 DequeLogger에서 사각형을 발견하면 그 정보를 클라이언트에게 전송하도록 함.
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
                case "재시작":
                    for(int i = 0;i<gameRoom.size();i++){
                        if(gameRoom.get(i)[0] == this){
                            if(gameRoom.get(i)[0].loginId == player1Name){
                                player1reStart = true;
                                if(player2reStart == false)gameRoom.get(i)[1].sendMessage("재시작버튼을 누르면 다시 시작합니다.");
                                else broadCast("재시작수락");
                            }
                            else if(gameRoom.get(i)[0].loginId == player2Name){
                                player2reStart = true;
                                if(player1reStart == false)gameRoom.get(i)[1].sendMessage("재시작버튼을 누르면 다시 시작합니다.");
                                else broadCast("재시작수락");
                            }
                        }
                        else if(gameRoom.get(i)[1] == this){
                            if(gameRoom.get(i)[1].loginId == player1Name){
                                player1reStart = true;
                                if(player2reStart == false)gameRoom.get(i)[0].sendMessage("재시작버튼을 누르면 다시 시작합니다.");
                                else broadCast("재시작수락");
                            }
                            else if(gameRoom.get(i)[1].loginId == player2Name){
                                player2reStart = true;
                                if(player1reStart == false)gameRoom.get(i)[0].sendMessage("재시작버튼을 누르면 다시 시작합니다.");
                                else broadCast("재시작수락");
                            }
                        }
                        if(player1reStart == true && player2reStart == true){
                            rectPoint ="";
                            list.clear();
                            player1.clear();
                            player2.clear();
                            player1RectNum = 0;
                            player2RectNum = 0;
                            player1reStart = false;
                            player2reStart = false;
                        }
                    }
                    break;
                default:
                    broadCast(msg);
                    handleGameTurn(msg);
                    break;
                    //게임진행
            }
        }
        // 매칭 기능 및 게임시작 기능
        private void handleMatching() {
            if (waitingRoom.isEmpty()) {
                this.sendMessage("당신의아이디는"+loginId);
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

                player1Name = matchedUser.loginId;
                player2Name = this.loginId;
                broadCast("게임시작");
                broadCast(player1Name+"턴입니다.");
            }
        }
        //게임 진행 기능
        private void handleGameTurn(String msg){
            String[] lineData = msg.split(":");
            if(lineData[0].equals(player1Name)){
                player1.add(lineData[1]);
                broadCast(player2Name+"턴입니다.");
            }
            else if(lineData[0].equals(player2Name)){
                player2.add(lineData[1]);
                broadCast(player1Name+"턴입니다.");
            }
        }
        // 매칭 취소 기능
        private void handleMatchingCancel() {
            synchronized (waitingRoom) {
                waitingRoom.removeIf(user -> user == this);
                broadCast(this.loginId + "님이 매칭을 종료했습니다.");
            }
        }
        // 게임 종료 기능
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
        //클라이언트 상태 전달 및 게임 진행 중계.
        private void broadCast(String msg){
            for (User u : users) {
                u.sendMessage(msg);
            }
        }
        private void sendMessage(String msg){
            try{
                chatSender.write(msg+"\n");
                chatSender.flush();
            }
            catch(IOException e){
                    System.out.println("326SendMessage");
            }
        }
    }

    public static void main(String[] args){
        String address = "localhost";
        String port = "1234";
        new Server(address , port);
    }

    interface DequeObserver {
        void onElementAdded(String element, Deque<String> deque);
    }
    class ObservableDeque {
        private final Deque<String> deque = new ArrayDeque<>();
        private final List<DequeObserver> observers = new ArrayList<>(); // player1, player2 두명의 선을 관리

        public void addObserver(DequeObserver observer) {
            observers.add(observer);
        }

        public void add(String element) {
            deque.add(element);
            for (DequeObserver observer : observers) {
                observer.onElementAdded(element, deque);
            }
        }
        public void clear(){
            deque.clear();
        }
    }
    //ObservableDeque는 플레이어가 추가한 선들을 저장하는 기능을 추가하고 DequeLogger를 자동 호출한다.
    class DequeLogger implements DequeObserver {
        @Override
        public void onElementAdded(String element, Deque<String> deque) {
            rectPoint = checkSquare(deque);
        }
        //선을 추가하면 같이 호출됨.
        //선을 추가한 뒤 지금까지 만든 선들로 사각형이 구성되는지 판단. 새로운 사각형이 형성되면 좌표를 반환.
        private String checkSquare(Deque<String> deque) {
            // 사각형을 이루는 선을 확인
            int count = 0;
            for (String lineData1 : deque) {
                String[] points1 = lineData1.split(",");
                int x1a = Integer.parseInt(points1[0]);
                int y1a = Integer.parseInt(points1[1]);
                int x2a = Integer.parseInt(points1[2]);
                int y2a = Integer.parseInt(points1[3]);

                if(y1a == y2a){
                    // 선이 수평이라면
                    String r2 = sortLine(x1a,y1a,x1a,y1a+1);
                    String r3 = sortLine(x2a,y2a,x2a,y1a+1);
                    String r4 = sortLine(x1a,y1a+1,x2a,y1a+1);
                    // r2,r3,r4는 선이 수평인 경우 사각형이 형성되기 위해 필요한 선 정보이다.

                    for (String lineData2 : deque) {
                        if (lineData1.equals(lineData2)) continue; // 동일한 선은 제외
                        String c = comparisonLine(lineData2);
                        if(c.equals(r2)||c.equals(r3)||c.equals(r4)){
                            count++;
                        }
                    }
                    if(count == 3){
                        if(!checkExistedSquare(x1a+","+y1a+","+(x2a)+","+(y1a+1))){
                            count = 0;
                            continue;
                        }
                        // 전에 만든 사각형 정보는 클라이언트가 이미 가지고 있어 다른 사각형을 찾도록 한다.
                        list.add(x1a+","+y1a+","+(x2a)+","+(y1a+1));
                        // 이번에 새로 찾은 사각형은 다음 사각형 찾기에서 걸러져야 된다. 따라서 list에 추가한다.
                        return x1a+","+y1a+","+(x2a)+","+(y1a+1);
                    }
                    else count = 0;
                }
                else{
                    //선이 수직인 경우
                    String r2 = sortLine(x1a,y1a,x1a+1,y1a);
                    String r3 = sortLine(x2a,y2a,x2a+1,y2a);
                    String r4 = sortLine(x1a+1,y1a,x2a+1,y2a);
                    // r2,r3,r4는 선이 수직인 경우 사각형이 형성되기 위해 필요한 선 정보이다.

                    for (String lineData2 : deque) {
                        if (lineData1.equals(lineData2)) continue; // 동일한 선은 제외
                        String c = comparisonLine(lineData2);
                        if(c.equals(r2)||c.equals(r3)||c.equals(r4)){
                            count++;
                        }
                    }
                    if(count == 3){
                        if(!checkExistedSquare(x1a+","+y1a+","+(x1a+1)+","+y2a)){
                            count = 0;
                            continue;
                            // 전에 만든 사각형 정보는 클라이언트가 이미 가지고 있어 다른 사각형을 찾도록 한다.
                        }
                        list.add(x1a+","+y1a+","+(x1a+1)+","+y2a);
                        // 이번에 새로 찾은 사각형은 다음 사각형 찾기에서 걸러져야 된다. 따라서 list에 추가한다.
                        return x1a+","+y1a+","+(x1a+1)+","+y2a;
                    }
                    else count = 0;
                }
            }
            return "";
        }
        //사각형인지 아닌지 판단하는 기능.
        private String comparisonLine(String lineData2){
            String[] points2 = lineData2.split(",");
            int x1b = Integer.parseInt(points2[0]);
            int y1b = Integer.parseInt(points2[1]);
            int x2b = Integer.parseInt(points2[2]);
            int y2b = Integer.parseInt(points2[3]);
            return sortLine(x1b,y1b,x2b,y2b);
        }
        //사각형을 만들기 위해 필요한 선과 비교하기 위해 sortLine를 호출하여 lineData2라는 선을 가공한다.
        private String sortLine(int x1a,int y1a,int x2a,int y2a){
            String s = x1a+","+y1a+","+x2a+","+y2a;

            if(x1a > x2a){
                s = x2a+","+y2a+","+x1a+","+y1a;
            }
            else if(x1a == x2a){
                if(y1a > y2a){
                    s = x2a+","+y2a+","+x1a+","+y1a;
                }
            }
            return s;
        }
        //선을 표현할 수 있는 경우는 2가지 이므로
        // 하나의 기준을 잡고 선을 표현해야 정확하게 사각형인지 아닌지 판단할 수 있기 때문에 선을 정렬시킴.
        private boolean checkExistedSquare(String str){
            if(list.size() != 0){
                for(String square: list){
                    if(square.equals(str))return false;
                }
                return true;
            }
            else return true;
        }
        // 이번턴에 선을 추가해서 새롭게 만들 수 있는 사각형이 있는지 판단한다.
        // 선을 버리지 않고 모아두기 때문에 이미 만든 사각형을 또 찾게 될 수 있다.
        // 따라서 이미 만든 사각형의 정보가 담긴 list와 비교 검사하여 checkSquare애서는 새로 찾은 사각형만 반환하도록 한다.
    }
    //DequeLogger는 추가된 선들이 사각형을 형성하는지 체크한다.
}
