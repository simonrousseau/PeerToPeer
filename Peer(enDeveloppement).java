import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;



public class Peer {
    static final String REQUETE_DEMANDE = "DemandeHash";
    static final String REQUETE_SUCCE = "RechercheSuccesseur";
    static final String REQUETE_MODIF_SUCCE = "ChangedeSuccesseur";
    static final String REQUETE_MODIF_PREDE ="ChangePredecesseur";
    static final String REQUETE_ROUTE = "rt?";
    static final String REQUETE_SUCCE_PROCHE = "QuiestlePlusProche";
    
    private String ipServeur = "172.21.65.115";
    
    private LigneTable[] tableRoutage;
    
    private String monIp;
    private int monHash;
    
    private String nextIp;
    private int nextHash;
    
    private String prevIp;
    private int prevHash;
    
    public Peer() throws UnknownHostException, IOException{
        
        //Demande de hash avec le serveur hash et le renvoi
        this.monIp = Inet4Address.getLocalHost().getHostAddress();
        this.monHash = Integer.parseInt(this.envoiRecupSocket(this.ipServeur, 8001, this.monIp));
                
        //Demande de connexion au serveur et renvoi sa réponse
        String question = "yo:"+this.monHash+":"+this.monIp;
        String reponseServWelcome = this.envoiRecupSocket(this.ipServeur, 8000, question);
        this.tableRoutage = new LigneTable[6];
        for(int i = 1; i <= 6;i++){
            this.tableRoutage[i-1] = new LigneTable("0.1.2.3", 0);
        }
        //On test les réponses possible du serveur welcome
        if(reponseServWelcome.equals("yaf")){
            //Cas où l'on est le premier
            System.out.println("Vous etes le premier !");
            //Donc on est notre propre successeur et predecesseur
            this.nextHash = this.monHash;
            this.nextIp = this.monIp;
            this.prevHash = this.monHash;
            this.prevIp = this.monIp;
            
            //On rempli la table de routage du premier
            for(int i = 1; i <= 6;i++){
                this.tableRoutage[i-1].setIp(this.monIp);
                this.tableRoutage[i-1].setHash(this.monHash);
            }
        }
        else{
            if(reponseServWelcome.equals("wrq")){
                //Cas où l'on a provoqué une erreur
                System.out.println("Probleme de connexion !");
                this.nextHash = -1;
                this.nextIp = null;
            }
            else{
                //Cas où l'on a réussi la connexion mais l'on est pas premier
                System.out.println("Connexion reussie !");
                //Associe l'ip à son successeur
                this.nextIp = reponseServWelcome;
                this.connexionNotFirst();
            }
        }
        
        //Création du thread qui écoute s'il y a une connexion provenant d'un autre pair
        Thread thread = new Thread(new PeerRunnable(this));
        thread.start();
        
        //Création du thread qui écoute s'il y a une connexion provenant de moniteur
        Thread threadMonitoring = new Thread(new MonitorRunnable(this));
        threadMonitoring.start();
    }
    
    public void connexionNotFirst() throws UnknownHostException, IOException{
        //Récupération du port du successeur
        int port = this.recupPort(this.nextIp);
        
        this.envoiVersSocket(this.nextIp, port, REQUETE_SUCCE+":"+this.monIp+":"+this.monHash);
        
        ServerSocket socketServeur = new ServerSocket(this.recupPort(this.monIp));
        
        //On attend une réponse d'un pair du réseau
        Socket socket = socketServeur.accept();
        
        //On récupère cette réponse
        InputStream fluxEntree = socket.getInputStream();
        BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
        String reponse = entree.readLine();
        
        System.out.println("Reçoi :"+reponse);
        
        //On ferme toute nos connexion
        socket.close();
        socketServeur.close();
        
        //On prépare la réponse à être utilisé
        String[] reponses = reponse.split(":");
        
        //Connexion avec notre successeur et predecesseur
        this.nextHash = Integer.parseInt(reponses[2]);
        this.nextIp = reponses[3];
        this.prevHash = Integer.parseInt(reponses[0]);
        this.prevIp = reponses[1];
        
        System.out.println("J'ai un nouveau successeur\n"+this.nextHash+" "+this.nextIp+"\nEt un nouveau prédecesseur\n"+this.prevHash+" "+this.prevIp);

        //Prévient le prédécesseur qu'il faut changer de successeur
        this.envoiVersSocket(this.prevIp, this.recupPort(this.prevIp), REQUETE_MODIF_SUCCE+":"+this.monHash+":"+this.monIp);
        
        //Prévient le successeur qu'il faut changer de prédécesseur
        this.envoiVersSocket(this.nextIp, this.recupPort(this.nextIp), REQUETE_MODIF_PREDE+":"+this.monHash+":"+this.monIp);
        
        this.majTable();
    }
    
    
    public void majTable(){
        try{
            for(int i = 1; i <= 6;i++){
                int k = this.monHash + (int)Math.pow(2, i);
                int k_recalculer = k;
                if (k > 100){
                    k_recalculer -= 100;
                }
                LigneTable ligne = this.recupererSuccesseur(k_recalculer);
                this.tableRoutage[i-1] = ligne;
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public LigneTable recupererSuccesseur(int k) throws UnknownHostException, IOException{
        LigneTable ligne;
        if ((this.monHash <= k && k < this.nextHash) || (this.monHash > this.nextHash && (k >= this.monHash || k < this.nextHash))){
            ligne = new LigneTable(this.nextIp, this.nextHash);
        }
        else{
            int port = this.recupPort(this.nextIp);
            
            this.envoiVersSocket(this.nextIp, port, REQUETE_SUCCE_PROCHE+":"+this.monIp+":"+this.monHash+":"+k);
            
            ServerSocket socketServeur = new ServerSocket(this.recupPort(this.monIp));
            
            //On attend une réponse d'un pair du réseau
            Socket socket = socketServeur.accept();
            
            //On récupère cette réponse
            InputStream fluxEntree = socket.getInputStream();
            BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
            String reponse = entree.readLine();
            
            System.out.println("Reçoi :"+reponse);
            
            //On ferme toute nos connexion
            socket.close();
            socketServeur.close();
            
            String[] reponseFormer = reponse.split(":");
            
            ligne = new LigneTable(reponseFormer[1], Integer.parseInt(reponseFormer[0]));
        }
        
        return ligne;
    }
    
    //Méthode renvoyant le port lié à une ip
    
    //Méthode renvoyant le port lié à l'ip en entrée
    public int recupPort(String ip){
        String[] ipCouper = ip.split("\\.");
        return 1000 + Integer.parseInt(ipCouper[3]);
    }
    
    //Méthode renvoyant le réponse de la machine à l'ip et le port en entrée
    
    //Méthode permettant d'envoyer envoi à un socket lié à l'ip et le port d'entrée
    public void envoiVersSocket(String ip, int port, String envoi) throws UnknownHostException, IOException{
        Socket socket = new Socket(ip, port);
        
        OutputStream fluxSortie = socket.getOutputStream();
        PrintWriter sortie = new PrintWriter(fluxSortie,true);
        sortie.println(envoi);
        
        System.out.println("Envoi de : "+envoi);
        
        socket.close();
    }
    
    //Méthode envoyant un message envoi à la machine ayant l'ip et le port en entrée
    
    //Méthode permettant de récupérer la réponse socket à l'ip en entrée
    public String recupDeStocket(String ip, int port) throws UnknownHostException, IOException{
        Socket socket = new Socket(ip, port);
        
        InputStream fluxEntree = socket.getInputStream();
        BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
        String reponse = entree.readLine();
        
        System.out.println("Reçoi :"+reponse);
        
        socket.close();
        
        return reponse;
    }
    
    //Méthode envoyant un message envoi à la machine ayant l'ip et le port en entrée et renvoyant sa réponse
    
    //Méthode permettant d'envoyer envoi à un socket lié à l'ip et le port d'entrée et renvoi la réponse
    public String envoiRecupSocket(String ip, int port, String envoi) throws UnknownHostException, IOException{
        
        Socket socket = new Socket(ip, port);
        
        OutputStream fluxSortie = socket.getOutputStream();
        PrintWriter sortie = new PrintWriter(fluxSortie,true);
        sortie.println(envoi);
        
        System.out.println("Envoi de : "+envoi);
        
        InputStream fluxEntree = socket.getInputStream();
        BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
        String reponse = entree.readLine();
        
        System.out.println("Reçoi :"+reponse);
        
        socket.close();
        
        return reponse;
    }
    
    //La classe Runnable implémenter pour attendre les connexion des autres pair

    public class PeerRunnable implements Runnable{
        public Peer pair;
        
        public PeerRunnable(Peer pair){
            this.pair = pair;
        }
        
        public void run(){
            int port = pair.recupPort(pair.monIp);
            
            try{
                //On s'ouvre au connexion
                ServerSocket socketServeur = new ServerSocket(port);
            
                while(true){
                    //On attend de recevoir une connexion
                    Socket socket = socketServeur.accept();
                    
                    //La connexion est reçu, on récupère ce que l'on nous envoi
                    InputStream fluxEntree = socket.getInputStream();
                    BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
                    String entreeLue = entree.readLine();
                    
                    System.out.println("Reçoi :"+entreeLue);
                    
                    if (entreeLue != null) {
                        // On découpe le message dans un format utilisable
                        String[] words = entreeLue.split(":");
                        switch(words[0]){
                        case REQUETE_DEMANDE:
                            //Cas où c'est une demande de hash
                            OutputStream fluxSortieHash = socket.getOutputStream();
                            PrintWriter sortieHash = new PrintWriter(fluxSortieHash,true);
                            sortieHash.println(pair.monHash);
                            
                            System.out.println("Envoi de : "+pair.monHash);
                            break;
                        case REQUETE_SUCCE:
                            //Cas où une personne cherche son successeur
                            socket.close();
                            this.trouverSuccesseur(words);
                            break;
                        case REQUETE_MODIF_SUCCE:
                            //Cas où je doit changer de successeur
                            socket.close();
                            this.modifierSuccesseur(socket, words);
                            break;
                        case REQUETE_MODIF_PREDE:
                            //Cas où je doit changer de predecesseur
                            this.modifierPredecesseur(socket, words);
                            break;
                        case REQUETE_SUCCE_PROCHE:
                            //Cas où une personne cherche le successeur d'un autre hash
                            this.rechercheSuccesseur(words);
                            break;
                        }
                        
                    }
                    socket.close();
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        
        public void rechercheSuccesseur(String[] words) throws UnknownHostException, IOException{
            String ipReponse = words[1];
            int hashReponse = Integer.parseInt(words[2]);
            int hashTest = Integer.parseInt(words[3]);
            
            if((pair.monHash <= hashTest && hashTest < pair.nextHash) || (pair.monHash > pair.nextHash && (hashTest >= pair.monHash || hashTest < pair.nextHash))){
                //Cas où je suis le seul dans le réseau avec le nouveau ou qu'il se situe entre moi et mon successeur
                System.out.println("Je passe dans cette boucle avec : "+hashTest);
                //On crée une connexion au nouveau
                Socket socket = new Socket(ipReponse, pair.recupPort(ipReponse));
                
                //On lui dit qui est son successeur et son prédécesseur
                OutputStream fluxSortie = socket.getOutputStream();
                PrintWriter sortie = new PrintWriter(fluxSortie,true);
                sortie.println(pair.nextHash+":"+pair.nextIp);
                
                System.out.println("JE PASSE \nEnvoi de : "+pair.nextHash+":"+pair.nextIp);
                socket.close();
            }
            else{
                //On crée une connexion à notre successeur
                Socket socket = new Socket(pair.nextIp, pair.recupPort(pair.nextIp));
                
                //On lui demande le successeur du nouveau
                OutputStream fluxSortie = socket.getOutputStream();
                PrintWriter sortie = new PrintWriter(fluxSortie,true);
                sortie.println(REQUETE_SUCCE_PROCHE+":"+ipReponse+":"+hashReponse+":"+hashTest);
                
                System.out.println(REQUETE_SUCCE_PROCHE+":"+ipReponse+":"+hashReponse+":"+hashTest);
                socket.close();
            }
        }
        
        //Méthode gérant le cas ou l'on cherche le successeur de quelqu'un
        
        public void trouverSuccesseur(String[] words) throws IOException{
            String ipNouv = words[1];
            int hashNouv = Integer.parseInt(words[2]);
            
            if((pair.monHash == pair.nextHash) || (pair.monHash < hashNouv && pair.nextHash > hashNouv) || (pair.nextHash < pair.monHash && (pair.nextHash > hashNouv || pair.monHash < hashNouv))){
                //Cas où je suis le seul dans le réseau avec le nouveau ou qu'il se situe entre moi et mon successeur
                System.out.println("Je passe dans cette boucle avec : "+hashNouv);
                //On crée une connexion au nouveau
                Socket socket = new Socket(ipNouv, pair.recupPort(ipNouv));
                
                //On lui dit qui est son successeur et son prédécesseur
                OutputStream fluxSortie = socket.getOutputStream();
                PrintWriter sortie = new PrintWriter(fluxSortie,true);
                sortie.println(pair.monHash+":"+pair.monIp+":"+pair.nextHash+":"+pair.nextIp);
                
                System.out.println("Envoi de : "+pair.monHash+":"+pair.monIp+":"+pair.nextHash+":"+pair.nextIp);
                socket.close();
            }
            else{
                //On crée une connexion à notre successeur
                Socket socket = new Socket(pair.nextIp, pair.recupPort(pair.nextIp));
                
                //On lui demande le successeur du nouveau
                OutputStream fluxSortie = socket.getOutputStream();
                PrintWriter sortie = new PrintWriter(fluxSortie,true);
                sortie.println(REQUETE_SUCCE+":"+ipNouv+":"+hashNouv);
                
                System.out.println(REQUETE_SUCCE+":"+ipNouv+":"+hashNouv);
                socket.close();
            }
        }
        
        //Méthode gérant le cas ou l'on modifie notre successeur
        
        public void modifierSuccesseur(Socket socket, String[] words){
            pair.nextHash = Integer.parseInt(words[1]);
            pair.nextIp = words[2];
            System.out.println("J'ai un nouveau successeur\n"+pair.nextHash+" "+pair.nextIp);
        }
        
        //Méthode gérant le cas ou l'on modifie notre predecesseur
        
        public void modifierPredecesseur(Socket socket, String[] words){
            pair.prevHash = Integer.parseInt(words[1]);
            pair.prevIp = words[2];
            System.out.println("J'ai un nouveau predecesseur\n"+pair.prevHash+" "+pair.prevIp);
        }
    }
    
    //La classe Runnable implémenter pour attendre les connexion du moniteur
    
    public class MonitorRunnable implements Runnable{
        public Peer pair;
        
        public MonitorRunnable(Peer pair){
            this.pair = pair;
        }
        
        public void run(){
            try{
                //On s'ouvre au connexion
                ServerSocket socketServeur = new ServerSocket(8002);
            
                while(true){
                    //On attend de recevoir une connexion
                    Socket socket = socketServeur.accept();
                    
                    //La connexion est reçu, on récupère ce que l'on nous envoi
                    InputStream fluxEntree = socket.getInputStream();
                    BufferedReader entree = new BufferedReader(new InputStreamReader (fluxEntree));
                    String entreeLue = entree.readLine();
                    
                    System.out.println("Reçoi du moniteur :"+entreeLue);
                    
                    if (entreeLue != null) {
                        // On découpe le message dans un format utilisable
                        String[] words = entreeLue.split(":");
                        switch(words[0]){
                        case REQUETE_ROUTE:
                            //Cas où le moniteur veut notre table de routage
                            this.communiquerTable(socket, words);
                            break;
                        }
                        
                    }
                    socket.close();
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }

        
        }
        
        //Méthode formatant notre table de routage et la renvoyant au moniteur
        
        public void communiquerTable(Socket socket, String[] words) throws IOException{
            String routes = "";
            routes += pair.monHash+":"+pair.nextHash+":"+pair.nextIp+"\n";
            
            for(int i = 1; i <= 6;i++){
                int k = pair.monHash + (int)Math.pow(2, i);
                int k_recalculer = k;
                if (k > 100){
                    k_recalculer -= 100;
                }
                routes += k_recalculer+":"+pair.tableRoutage[i-1].getHash()+":"+pair.tableRoutage[i-1].getIp()+"\n";
            }
            
            routes += "end\n";
            
            OutputStream fluxSortie = socket.getOutputStream();
            PrintWriter sortie = new PrintWriter(fluxSortie,true);
            sortie.println(routes);
            
            System.out.println("Envoi au moniteur de : "+routes);
            
            socket.close();
        }
    }
    
    public class LigneTable{
        public String ip;
        public int hash;
        
        public LigneTable(String ip, int hash){
            this.ip = ip;
            this.hash = hash;
        }
        
        public void setIp(String ip){
            this.ip = ip;
        }
        
        public void setHash(int hash){
            this.hash = hash;
        }
        
        public String getIp(){
            return this.ip;
        }
        
        public int getHash(){
            return this.hash;
        }
        
        public String toString(){
            return this.hash+" "+this.ip;
        }
    }
    
    public static void main (String []args) throws UnknownHostException, IOException, InterruptedException{
        Peer pair = new Peer();
    }
}





