import java.io.*;
import java.net.*;
import java.util.*;;
import java.nio.file.Files;
import java.nio.file.*;

public class CSftp {
	
	static int DEFAULT_PORT = 21;
	static List<String> acceptedCommands = Arrays.asList("user", "pw", "quit", "get", "features", "cd", "dir");
	
    public static void main(String [] args) {

		if(args.length < 1){
	    	System.err.println("998 Input error while reading commands, terminating.");
			System.exit(1);
		}
		
		String ip = args[0];
		int port = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
		Socket ftpSocket = OpenSocket(ip, port, 2000, false);
		
		try {
				PrintWriter fromClient = new PrintWriter(ftpSocket.getOutputStream(), true);
				BufferedReader fromServer = new BufferedReader(new InputStreamReader(ftpSocket.getInputStream()));
				
				String fromServerString = "";
				String fromClientString = "";
				String command = "";
			
				while (true) {
					
					if(command.contains("dir") || command.contains("get"))
						PassiveCommand(ftpSocket, fromClient, command);
					else
						ReadServerResponse(ftpSocket);

					System.out.print("csftp> ");					

					command = SendClientInput(ftpSocket, fromClient);					
				}
				
			} catch (IOException exception){
				System.err.println("0xFFFF Processing error. " + exception);
				System.exit(1);
		}
    }


	// Overload method
	static String ReadServerResponse(Socket socket){
		return ReadServerResponse(socket, false);
	}
	
	
	//Prints out the response from the server.
	//Returns the response string from the server.
	//Closes the socket and prints an error if there is a problem reading the response.
	static String ReadServerResponse(Socket socket, boolean pasv){
		
		String fromServerString = "";
		
		try{
				
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			while ((fromServerString = fromServer.readLine()) != null) {

				System.out.println("<-- " + fromServerString);
				
				if(fromServerString.contains("227")){
					byte[] b = fromServerString.getBytes();
					for(int i = 0; i < b.length; i++)
						System.out.printf("%d, %c\n", i, b[i]);
				}
				if(fromServerString.split(" ")[0].equals("221"))
					System.exit(1);	
				if(fromServerString.split(" ")[0].matches("\\d{3}"))
					break;
			}
		} catch (IOException exception){
			if(pasv){
				System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
				try{
					socket.close();
				} catch (IOException e){}
			} else {
				System.err.println ("0xFFFD Control connection I/O error, closing control connection.");
				try{
					socket.close();
				} catch (IOException e){}
				
				System.exit(1);
			}
		}
		
		return fromServerString;
	}
	
	
	//Reads the user input and sends it to the server
	//Returns the user input
	//Prints an error and exits if there is a problem reading the commands
	static String SendClientInput(Socket ftpSocket, PrintWriter fromClient) {

		String fromClientString = "";
		
		try{
			BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
			fromClientString = userInput.readLine().trim().replaceAll("\\t+", " ").replaceAll(" +", " ");
		
			while(!ValidCommand(fromClientString)) {
				
				if(acceptedCommands.contains(fromClientString.split(" ")[0]))
					System.out.println("0x002 Incorrect number of arguments.");
				else if(fromClientString.length() != 0 && !fromClientString.startsWith("#"))
					System.out.println("0x001 Invalid command.");
				
				System.out.print("csftp> ");
				fromClientString = userInput.readLine().trim().replaceAll("\\t+", " ").replaceAll(" +", " ");
			}
			
			String command = ParseCommand(fromClientString);
				
			if (fromClientString != null) {
				System.out.print("--> " + fromClientString + "\n");
				fromClient.println(command + "\r");
			}
			
		}catch (IOException exception) {
			System.err.println("0xFFFE Input error while reading commands, terminating.");
			System.exit(1);
		}
		
		return fromClientString;
	}
	
	
	
	//Creates new data connection
	//Calls DIRCommand or GETCommand depending on the command
	//Prints an error and exits if a processing error (0xFFFF) occrs
	static void PassiveCommand(Socket ftpSocket, PrintWriter fromClient, String command) {
		
		String fromServerString = ReadServerResponse(ftpSocket);
		/*
		byte[] b = fromServerString.getBytes();
		
		for(int i = 0; i < b.length; i++)
			System.out.printf("%d, %c, %c\n", i, b[i], b[i]);
			*/
		if(!fromServerString.split(" ")[0].equals("227"))
			return;
			
		int start = fromServerString.indexOf("(");
		int end = fromServerString.indexOf(")");
		String subFromServerString = fromServerString.substring(start + 1, end);
		String[] networkComponents = subFromServerString.split(",");
		String ip = networkComponents[0] + "." + networkComponents[1] + 
		"." + networkComponents[2] + "." + networkComponents[3];
		int port = (Integer.parseInt(networkComponents[4]) << 8) + Integer.parseInt(networkComponents[5]);
		
		Socket pasvSocket = OpenSocket(ip, port, 1000, true);
		
		if(pasvSocket == null)
			return;
			
		try {
				BufferedReader serverReader = new BufferedReader(new InputStreamReader(pasvSocket.getInputStream()));

				if(command.contains("dir"))
					DIRCommand(ftpSocket, pasvSocket, fromClient);
				else if(command.contains("get"))
					GETCommand(ftpSocket, pasvSocket, fromClient, command);
						
				pasvSocket.close();
				
			} catch (IOException exception){
				System.err.println("0xFFFF Processing error. " + exception);
				System.exit(1);
		}
	}
	
	
	//Sends the LIST command to the server
	//Calls ReadServerResponse on the control and data connections to print the results
	static void DIRCommand(Socket ftpSocket, Socket pasvSocket, PrintWriter fromClient) {
		
		String fromPASVSocketString = "";
		fromClient.println("LIST");

		String fromServerString = ReadServerResponse(ftpSocket);

		if(fromServerString.contains("150")){
			ReadServerResponse(pasvSocket, true);
			ReadServerResponse(ftpSocket);
		}
	}
	
	
	//Changes to binary mode and requests a file from the server
	//Saves the file to the local machine with the same name if the file transferred sucessfully
	//Prints an error and closes the data connection if the file cannot be saved
	static void GETCommand(Socket ftpSocket, Socket pasvSocket, PrintWriter fromClient, String command) {
			
		InputStream socketInput;
		String file = command.split("get")[1].trim();
		
		try{
			Path path = Paths.get(System.getProperty("user.dir") + "/" + file);

			String fromPASVSocketString = "";
			fromClient.println("TYPE I");
			ReadServerResponse(ftpSocket);

			fromClient.println("RETR " + file);
			String fromServerString = ReadServerResponse(ftpSocket);

			if(fromServerString.contains("150")){
				socketInput = GetPassiveSocketInputStream(ftpSocket, pasvSocket);
				if(socketInput == null)
					return;
				Files.copy(socketInput, path, StandardCopyOption.REPLACE_EXISTING);
				ReadServerResponse(ftpSocket);
			}
			
		} catch (Exception exception){
			ReadServerResponse(ftpSocket);
			System.err.println("0x38E Access to local file " + file + " denied.");
			try{
				pasvSocket.close();
			} catch (IOException e) {}
		}
	}
	
	
	//Creates and returns an InputStream for the data connection
	//Prints an error and closes the data connection if the InputStream could not be created
	static InputStream GetPassiveSocketInputStream(Socket ftpSocket, Socket pasvSocket){
		InputStream socketInput = null;

		try{
			socketInput = pasvSocket.getInputStream();
		} catch (IOException exception) {
			ReadServerResponse(ftpSocket);
			System.err.println("0x3A7 Data transfer connection I/O error, closing data connection.");
			try{
				pasvSocket.close();
			} catch (IOException e) {}
		}
		
		return socketInput;
	}
	
	
	//Opens a new socket with the specified parameters
	//Returns the new socket
	//Prints an error and returns null if the socket for a data connection could not be created
	//Prints an error and exits if the control socket could not be created
	static Socket OpenSocket (String ip, int port, int timeout, boolean pasv){
		
		Socket socket = new Socket();
		try{
			socket.connect(new InetSocketAddress(ip, port), timeout);
			
		} catch (IOException exception){
			if(pasv){
				System.err.println ("0x3A2 Data transfer connection to " + ip + " on port " + port + " failed to open");
				socket = null;
			}else{
				System.err.println ("0xFFFC Control connection to " + ip + " on port " + port + " failed to open.");
				System.exit(1);
			}
		}
		
		return socket;
	}
	
	
	//Parses the application command into an FTP command
	//Returns the respective FTP command
	static String ParseCommand(String fromClientString){
		
		String[] parameters = fromClientString.split(" ");
		String command = "";
		
		switch(parameters[0]){
			case "user":
				command = "USER";
				break;
			case "pw":
				command = "PASS";
				break;
			case "quit":
				command = "QUIT";
				break;
			case "features":
				command = "FEAT";
				break;
			case "cd":
				command = "CWD";
				break;
			case "dir":
			case "get":
				command = "PASV";
				break;
			default:
				command = "";
				break;
		}
		for(int i = 1; i < parameters.length; i++)
			command += " " + parameters[i];
			
		return command;
	}
	
	
	//Returns true if the command is a valid command
	//Returns false otherwise
	static boolean ValidCommand(String fromClientString){
		
		String[] parameters = fromClientString.split(" ");
		
		if(!acceptedCommands.contains(parameters[0]))
			return false;

		switch(parameters[0]){
			case "user":
			case "pw":
			case "get":
			case "cd": //!!!TODO: make sure there arent too many parameters
				if(parameters.length != 2)
					return false;
				break;
			default:
				if(parameters.length != 1)
					return false;
				break;
		}
		return true;
	}
}