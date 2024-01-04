import java.io.*;
import java.net.*;
import java.nio.file.Files;

public class Client {
    public static void main(String[] args) {
        try {
            // Continuously interact with the server
            while (true) {
                // Establish a connection to the server
                Socket clientSocket = new Socket("localhost", 8080);

                // Setup input and output streams for communication
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // User interaction to input file name, option, and count option
                System.out.println("Enter text file name (without extension):");
                String fileName = userInput.readLine();
                System.out.println("Enter option (store/get/read/totals/update/remove/exit):");
                String option = userInput.readLine();

                // Conditionals that determines how the options will be sent to the server
                if (option.equals("store") || option.equals("update") || option.equals("remove") || option.equals("exit") || option.equals("totals")) {
                    sendFileToServer(fileName, option, clientSocket, out, in, userInput);
                } else {
                    // If the initial conditional is false, then the user is allowed to add another request for a count option
                    System.out.println("Enter count option (lines/words/characters):");
                    String countOption = userInput.readLine();

                    // Construct the full message to send to the server
                    String messageToSend = fileName + "," + option + "," + countOption;
                    out.println(messageToSend);

                    // Writes the full response from the server
                    serverResponse(in);
                }
                clientSocket.close(); // Close the socket after completing communication with the server
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method that determines how the store, update, totals, remove, and exit options are handled
    private static void sendFileToServer(String fileName, String option, Socket clientSocket, PrintWriter out, BufferedReader in, BufferedReader userInput) throws IOException {
        out.println(fileName + "," + option); // Sends this message to the server

        // Handling for both store and update options
        if (option.equals("store") || option.equals("update")) {
            System.out.println("Enter the file path to upload:");
            String filePath = userInput.readLine();

            // Read and send the file content to the server
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                OutputStream os = clientSocket.getOutputStream();
                os.write(fileContent, 0, fileContent.length);
                os.flush();
                serverResponse(in);
            } else {
                System.out.println("File not found or invalid path.");
            }
        // Handling for remove and totals
        } else if (option.equals("remove") || option.equals("totals")){
            serverResponse(in);
        // Handling for exit message
        } else {
            System.out.println("Exit command sent to server. Terminating client.");
            System.exit(0);
        }
    }

    private static void serverResponse(BufferedReader in) throws IOException {
        // Writes the full response from the server
        String serverResponse;
        while ((serverResponse = in.readLine()) != null) {
            System.out.println(serverResponse);
        }
    }
}