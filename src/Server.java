import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

public class Server {
    private static CacheService<String, String> sharedCache = new CacheService<>(6); // Creates a shared cache with a maximum size of 6 keys in the cache

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            Semaphore serverSemaphore = new Semaphore(5); // Limits to 5 simultaneous connections

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket, serverSemaphore, sharedCache));
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Semaphore semaphore;
    private final CacheService<String, String> clientCache;
    File programDirectory = new File("src/storageFiles");

    /**
     * Constructor for ClientHandler.
     * @param clientSocket Socket for client-server communication
     * @param semaphore Semaphore limiting simultaneous connections
     * @param sharedCache Shared cache among clients
     */
    public ClientHandler(Socket clientSocket, Semaphore semaphore, CacheService<String, String> sharedCache) {
        this.clientSocket = clientSocket;
        this.semaphore = semaphore;
        this.clientCache = sharedCache;
    }

    @Override
    public void run() {
        try {
            semaphore.acquire(); // Acquire semaphore before handling client request

            // Setup input and output streams for communication
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Process client message
            String clientMessage = in.readLine();
            String response = processClientMessage(clientMessage);

            // Send response to Client
            out.println(response);

            semaphore.release(); // Release semaphore after handling client request
            clientSocket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes the client's message and generates a response.
     * @param message Client's message sent to the server
     * @return Response generated based on the client's message
     */
    private String processClientMessage(String message) {
        String[] parts = message.split(",");
        String fileName = parts[0].trim() + ".txt";
        String option = parts[1].trim();
        StringBuilder response = new StringBuilder("Processing " + fileName + ": \n");

        if (parts.length == 3) {
            String countOption = parts[2].trim();

            // Check cache for options
            String cachedOption = clientCache.handleServerReadRequest(fileName + "," + option);
            String cachedCountOption = clientCache.handleServerReadRequest(fileName + "," + countOption);

            if (cachedOption != null) {
                response.append("\nCache hit for ").append(fileName).append(", ").append(option).append(": ").append(cachedOption);
            }
            if (cachedCountOption != null) {
                response.append("\nCache hit for ").append(fileName).append(", ").append(countOption).append(": ").append(cachedCountOption);
            }

            // If any of the options aren't found in cache, perform the respective actions
            if (cachedOption == null) {
                switch (option) {
                    case "get":
                        response.append("\nFiles on server: ").append(getFileNames());
                        break;
                    case "read":
                        response.append("\nContent of ").append(fileName).append(": ").append(readFileContent(fileName));
                        break;
                    default:
                        return "\nInvalid option. Supported options: lines, words, characters, get_file_names, read_file_content, get_system_totals, exit, store";
                }
                // Store response in cache
                clientCache.handleServerWriteRequest(fileName + "," + option, response.toString());
            }
            if(cachedCountOption == null){
                int lines = 0, words = 0, characters = 0;

                // Calculate and cache count options if they were not found in the cache
                switch (countOption) {
                    case "lines":
                        lines = countLines(fileName);
                        clientCache.handleServerWriteRequest(fileName + "," + countOption, String.valueOf(lines));
                        response.append("\nLine count for ").append(fileName).append(": ").append(lines);
                        break;
                    case "words":
                        words = countWords(fileName);
                        clientCache.handleServerWriteRequest(fileName + "," + countOption, String.valueOf(words));
                        response.append("\nWord count for ").append(fileName).append(": ").append(words);
                        break;
                    case "characters":
                        characters = countCharacters(fileName);
                        clientCache.handleServerWriteRequest(fileName + "," + countOption, String.valueOf(characters));
                        response.append("\nCharacter count for ").append(fileName).append(": ").append(characters);
                        break;
                    default:
                        return "Invalid count option.";
                }
            }
        } else if(parts.length == 2){
            switch (option) {
                case "update":
                    // Receive and store the updated file content
                    String updateResponse = updateFile(fileName);
                    response.append(updateResponse);
                    break;
                case "remove":
                    // Remove the file from the server
                    String removeResponse = removeFile(fileName);
                    response.append(removeResponse);
                    break;
                case "store":
                    // Store the file in the server
                    String storeResponse = storeFile(fileName);
                    response.append(storeResponse);
                    break;
                case "totals":
                    response.append("\nSystem totals: ").append(getSystemTotals(fileName));
                    break;
                case "exit":
                    return "Client termination requested.";
                default:
                    return "Invalid option.";
            }
        }
        else {
            return "Invalid message format. Please provide the file name, option, and count option as prompted by the Client.";
        }
        // Return response to client
        response.append("\nDone.");
        return response.toString();
    }

    // Counts the lines in the file
    private int countLines(String fileName) {
        int lineCount = 0;
        try {
            Path filePath = Paths.get(programDirectory.getAbsolutePath(), fileName);
            lineCount = (int) Files.lines(filePath).count();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lineCount;
    }

    // Counts the words in a file
    private int countWords(String fileName) {
        int wordCount = 0;
        try {
            Path filePath = Paths.get(programDirectory.getAbsolutePath(), fileName);
            String content = new String(Files.readAllBytes(filePath));
            wordCount = content.split("\\s+").length;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return wordCount;
    }

    // Counts the characters in a file
    private int countCharacters(String fileName) {
        int charCount = 0;
        try {
            Path filePath = Paths.get(programDirectory.getAbsolutePath(), fileName);
            String content = new String(Files.readAllBytes(filePath));
            charCount = content.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return charCount;
    }

    // Stores a file passed in by the client
    private String storeFile(String fileName) {
        try {
            InputStream is = clientSocket.getInputStream();
            FileOutputStream fos = new FileOutputStream(new File(programDirectory, fileName));
            byte[] buffer = new byte[8192];
            int bytesRead = is.read(buffer);
            fos.write(buffer, 0, bytesRead);
            fos.close();
            return "File stored successfully.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to store the file.";
        }
    }

    // Updates the contents of a file with the one passed in by the client
    private String updateFile(String fileName) {
        try {
            InputStream is = clientSocket.getInputStream();
            FileOutputStream fos = new FileOutputStream(new File(programDirectory, fileName));
            byte[] buffer = new byte[8192];
            int bytesRead = is.read(buffer);
            fos.write(buffer, 0, bytesRead);
            fos.close();

            // Remove elements associated with the previous version of the file from the cache
            clientCache.handleServerRemovalRequest(fileName);
            return "File updated successfully.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to update the file.";
        }
    }

    // Removes the file passed in by the client
    private String removeFile(String fileName) {
        File fileToRemove = new File(programDirectory, fileName);
        if (fileToRemove.exists()) {
            boolean deleted = fileToRemove.delete();

            // Remove elements associated with the previous version of the file from the cache
            clientCache.handleServerRemovalRequest(fileName);
            if (deleted) {
                return "File removed successfully.";
            } else {
                return "Failed to remove the file.";
            }
        } else {
            return "File does not exist.";
        }
    }

    // Gets all the file names by printing the directory
    private String getFileNames() {
        File folder = programDirectory;
        File[] files = folder.listFiles();
        if (files != null) {
            StringBuilder fileNames = new StringBuilder();
            for (File file : files) {
                if (file.isFile()) {
                    fileNames.append(file.getName()).append(", ");
                }
            }
            if (fileNames.length() > 0) {
                return fileNames.substring(0, fileNames.length() - 2);
            }
        }
        return "No files found.";
    }

    // Reads all the contents of a file
    private String readFileContent(String fileName) {
        try {
            Path filePath = Paths.get(programDirectory.getAbsolutePath(), fileName);
            byte[] fileBytes = Files.readAllBytes(filePath);
            return new String(fileBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Failed to read file.";
    }

    // Gets the totals of lines/words/characters and caches them if they haven't been cached already
    private String getSystemTotals(String fileName) {
        StringBuilder response = new StringBuilder();

        String[] countOptions = {"lines", "words", "characters"};

        for (String countOption : countOptions) {
            String key = fileName + "," + countOption;
            String cachedValue = clientCache.handleServerReadRequest(key);

            if (cachedValue == null) {
                int count = 0;
                switch (countOption) {
                    case "lines":
                        count = countLines(fileName);
                        break;
                    case "words":
                        count = countWords(fileName);
                        break;
                    case "characters":
                        count = countCharacters(fileName);
                        break;
                }

                clientCache.handleServerWriteRequest(key, String.valueOf(count));
                response.append("\n").append(countOption).append(" count for ").append(fileName).append(": ").append(count);
            } else {
                response.append("\nCache hit for ").append(key).append(": ").append(cachedValue);
            }
        }
        return response.toString();
    }
}