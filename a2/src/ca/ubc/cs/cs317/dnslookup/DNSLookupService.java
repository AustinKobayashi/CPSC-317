package ca.ubc.cs.cs317.dnslookup;

import java.io.Console;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.io.*;
import javax.swing.plaf.synth.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();
    private static int pos = 0;
    
    private static Set<ResourceRecord> resourceRecords = new HashSet<ResourceRecord>();
    private static boolean resultIsQueryIp = false;
    private static DNSNode originalNode;
    private static boolean serverCname = false;
    private static int aCount = 0;
    private static int nsCount = 0;
    private static int additionalCount = 0;

    private static Set<String> serverCnames = new HashSet<String>();
    private static int failures = 0;
    private static int queryId = -1;
    private static boolean foundCname = false;
    private static boolean foundMX = false;
    
    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);

        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leadataInputg/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {
        
        ClearQueryGlobalVariables();
        DNSNode node = new DNSNode(hostName, type);
        originalNode = node;
        printResults(node, getResults(node, 0));
    }
    
    private static void ClearQueryGlobalVariables(){

        resourceRecords.clear();
        resultIsQueryIp = false;
        serverCname = false;
        aCount = 0;
        nsCount = 0;
        additionalCount = 0;
        serverCnames.clear();
        failures = 0;
        queryId = -1;
        foundCname = false;
        foundMX = false;
        pos = 0;
    }
    
    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regardataInputg CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records correspondataInputg to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {
        return getResults(node, indirectionLevel, rootServer);
    }
    
    
    
    // Overload method that specifies the server
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel, InetAddress server) {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }

        if(resultIsQueryIp || CachedCnameHasIp(originalNode, 0) || QueryResultsAreCached(node)){
            return cache.getCachedResults(originalNode);
        }
        
        
        // Gets cached results from the server
        Set<ResourceRecord> cachedResults = new HashSet<ResourceRecord>();
        cachedResults.addAll(cache.getCachedResults(node));
        cachedResults.addAll(cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.getByCode(5))));
        
        server = FindCachedServer(node, server, 0);
        

        if(cachedResults != null && cachedResults.size() != 0){
            for (ResourceRecord rr : cachedResults) {
                if(rr.getHostName().equals(originalNode.getHostName()) && rr.getType() == node.getType()){
                    return cache.getCachedResults(node);
                }
                
                if(!rr.getTextResult().equals(node.getHostName()) && rr.getType().getCode() == 5){

                    DNSNode nextNode = new DNSNode(rr.getTextResult(), node.getType());
                    ClearQueryGlobalVariables();
                    return getResults(nextNode, indirectionLevel + 1);
                }
            }
        }

        // Reset the number of timeouts and send a DNS query to the server
        failures = 0;
        
        // Retrieves the resource records from the server
        retrieveResultsFromServer(node, server);
        boolean dnsRequestNextAddress = false;
        boolean cname = false;
        boolean allRRSoa = true;
        
        ResourceRecord nextRR = null;
        
        for (ResourceRecord rr : resourceRecords) {
            
            if(!rr.getTextResult().equals("----"))
                allRRSoa = false;
                
            if(rr.getType().getCode() == 1 && rr.getInetResult() != null){

                dnsRequestNextAddress = true;
                nextRR = rr;
                break;
            }
            
            if(rr.getType().getCode() == 5){

                cname = true;
                nextRR = rr;
                break;
            }
            
            if(aCount == 0 && nsCount > 0 && additionalCount == 0){
                cname = true;
                serverCname = true;
                nextRR = rr;
                serverCnames.add(nextRR.getTextResult());
                break;
            }
        }
        
        
        if(verboseTracing && failures <= 1)
            verbosePrintResourceRecord(node);
        
        if(allRRSoa || (originalNode.getType() == RecordType.getByCode(5) && foundCname))
                return cache.getCachedResults(node);
 
        if(originalNode.getType() == RecordType.getByCode(15) && foundMX)
            return Collections.emptySet();
 
        if(cname){
            DNSNode nextNode = new DNSNode(nextRR.getTextResult(), node.getType());
            pos = 0;
            resourceRecords.clear();
            return getResults(nextNode, indirectionLevel + 1);
        }
        
        for(ResourceRecord rr : resourceRecords){
        
            if(rr.getHostName().equals(node.getHostName()) && rr.getInetResult() != null){
                pos = 0;
                resourceRecords.clear();
                serverCname = false;
                return getResults(originalNode, indirectionLevel + 1, rr.getInetResult());
            }
        }
        
        if(dnsRequestNextAddress){
            pos = 0;
            resourceRecords.clear();
            return getResults(node, indirectionLevel, nextRR.getInetResult());
        }
        
        return cache.getCachedResults(node);
    }
    
    
    
    // Returns true if the a cached cname for the node has a corresponding ip address, false otherwise
    // Will recurse down the cname "trail" until it finds an ip address or returns if it does not
    // If the cname "trail" is greater than MAX_INDIRECTION_LEVEL the method will return false. In
    // this case, "Maximum number of indirection levels reached." will be printed in getResults
    private static boolean CachedCnameHasIp(DNSNode node, int indirectionLevel){
        
        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            return false;
        }
        
        Set<ResourceRecord> cachedRecords = new HashSet<ResourceRecord>();
        cachedRecords.addAll(cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.getByCode(5))));
        
        if(node != originalNode){
            cachedRecords.addAll(cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.getByCode(1))));
            cachedRecords.addAll(cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.getByCode(28))));
        }

        for(ResourceRecord record : cachedRecords){
            
            if(((record.getInetResult() != null && record.getType() != RecordType.getByCode(5)) || record.getType() == RecordType.getByCode(28)) &&
                (!originalNode.getHostName().equals(node.getHostName()) && record.getType() == originalNode.getType())){
                ResourceRecord rr;
                
                if(record.getType() == RecordType.getByCode(28))
                    rr = new ResourceRecord(originalNode.getHostName(), originalNode.getType(), record.getTTL(), record.getTextResult());
                else
                    rr = new ResourceRecord(originalNode.getHostName(), originalNode.getType(), record.getTTL(), record.getInetResult());

                cache.addResult(rr);
                resourceRecords.add(rr);
                return true;
            }else
                return CachedCnameHasIp(new DNSNode(record.getTextResult(), record.getType()), indirectionLevel + 1);
        }

        return false;    
    }
    
    
    // Returns true if the query results are already stored in cache
    private static boolean QueryResultsAreCached(DNSNode node){
        Set<ResourceRecord> cachedResults = cache.getCachedResults(node);
        int type = node.getType().getCode();
        
        for(ResourceRecord record : cachedResults){
            switch(type){
                case 1:
                    if(record.getHostName().equals(node.getHostName()) && record.getInetResult() != null)
                        return true;
                    break;
                case 5:
                case 28:
                    if(record.getHostName().equals(node.getHostName()))
                        return true;
                    break;
                default:
                    break;
            }
        }
        return false;
    }
    
    
    
    // Finds a cached server starting at the top of the domain
    // Eg) if you search for www.cs.ubc.ca it will check if there is a cached server for:
    // www.cs.ubc.ca, then cs.ubc.ca, then ubc.ca, then ca
    // If a server is found it returns the ip address of the server
    private static InetAddress FindCachedServer(DNSNode node, InetAddress server, int index){

        String[] labels = node.getHostName().split("\\.");
        if(index >= labels.length)
            return server;
        
        int substringIndex = 0;
        for(int i = 0; i < index; i++){
            substringIndex = node.getHostName().indexOf(".", substringIndex+1) +1;
        }
        String name = node.getHostName().substring(substringIndex);

        Set<ResourceRecord> cachedResults = new HashSet<ResourceRecord>();
        cachedResults.addAll(cache.getCachedResults(new DNSNode(name, RecordType.getByCode(2))));
        
        Set<ResourceRecord> cachedNameServers = new HashSet<ResourceRecord>();
        for(ResourceRecord rr : cachedResults){
            cachedNameServers.addAll(cache.getCachedResults(new DNSNode(rr.getTextResult(), RecordType.getByCode(1))));

            for(ResourceRecord nameServer : cachedNameServers){

                if(nameServer.getInetResult() != null)
                    return nameServer.getInetResult();
            }
        }
        
        return FindCachedServer(node, server, index + 1);
    }
    
    
    
    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {
        try{
            
            Set<ResourceRecord> resourceRecords = new HashSet<ResourceRecord>();
            ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);
            
            byteArrayOutput = WriteQueryValuesToByteArray(node, server);
            SendDNSQuery(byteArrayOutput, server);
            
            byte[] response = new byte[65507];

            DatagramPacket replyPacket = new DatagramPacket(response, response.length);
            socket.receive(replyPacket);
            int replyPacketLength = replyPacket.getLength();
            
            ReadDNSResponseHeader(node, server, response);
            
            // Resource records start here
            ReadResponseResourceRecord(node, response);
            
            failures = 0;
                    
        } catch (IOException e){
            failures++;
            if(failures <= 1){
                retrieveResultsFromServer(node, server);
                return;
            }
        } 
    }
    
    
    
    // Writes the query values to a ByteArrayOutputStream then returns the ByteArrayOutputStream
    private static ByteArrayOutputStream WriteQueryValuesToByteArray(DNSNode node, InetAddress server) throws IOException{
        
        ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);

        //Query ID
        if(failures == 0)
            queryId = (short)((random.nextInt(0x00FFF) + 0x000001) & 0x0000FFFF);
            
        dataOutput.writeShort(queryId);
        
        // Write Query Flags
        dataOutput.writeShort(0x0000);

        // Question Count
        dataOutput.writeShort(0x0001);

        // Answer Record Count
        dataOutput.writeShort(0x0000);
        
        // NS Record Count
        dataOutput.writeShort(0x0000);

        // Additional Record Count
        dataOutput.writeShort(0x0000);

        String qName = node.getHostName();
        String[] labels = qName.split("\\.");
        
        for (int i = 0; i < labels.length; i++) {
            byte[] label = labels[i].getBytes("UTF-8");
            
            // Write length of qname octets
            dataOutput.writeByte(label.length);
            
            // Write octets for label
            dataOutput.write(label);
        }

        // Zero length octet to end qname
        dataOutput.writeByte(0x00);

        // qtype
        int type = node.getType().getCode() & 0x00FF;
        dataOutput.writeShort(type);
        
        // qclass (!!!should it always be 0x1 = IN?)
        dataOutput.writeShort(0x0001);
        
        if(verboseTracing)
            System.out.println("\n\nQuery ID     " + queryId + " " + node.getHostName() + "  " + node.getType() + " --> " + server.getHostAddress());

        return byteArrayOutput;
    }
    
    
    // Sends the DNS query packet 
    private static void SendDNSQuery(ByteArrayOutputStream byteArrayOutput, InetAddress server) throws IOException{
        
        byte[] udpPacket = byteArrayOutput.toByteArray();

        DatagramPacket requestPacket = new DatagramPacket(udpPacket, udpPacket.length, server, DEFAULT_DNS_PORT);
        socket.send(requestPacket);
    }
    
    
    // Reads the DNS response packet header
    private static void ReadDNSResponseHeader(DNSNode node, InetAddress server, byte[] response){
        
        int queryId = (((response[pos++]) << 8) | (response[pos++] & 0xFF)) & 0x0000FFFF;            

        short flags = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
        
        int qr = (flags >> 15) & 0x1;
        int opcode = (flags >> 11) & 0xF;
        int authoritativeAns = (flags >> 10) & 0x1;
        int tc = (flags >> 9) & 0x1;
        int rd = (flags >> 8) & 0x1;
        int ra = (flags >> 7) & 0x1;
        int z = (flags >> 4) & 0x7;
        int rcode = (flags >> 0) & 0xF;
    
        int qCount = (((response[pos++]) << 8) | (response[pos++] & 0xFF)) & 0x0000FFFF; 
        
        aCount = (((response[pos++]) << 8) | (response[pos++] & 0xFF)) & 0x0000FFFF; 
            
        nsCount = (((response[pos++]) << 8) | (response[pos++] & 0xFF)) & 0x0000FFFF; 
        
        additionalCount = (((response[pos++]) << 8) | (response[pos++] & 0xFF)) & 0x0000FFFF; 

        int octetLength = 0;
        String qname = "";
        while ((octetLength = response[pos++]) != 0) {

            for (int i = 0; i < octetLength; i++) 
                qname += (char)response[pos++];
        
            qname += ".";  
        }

        qname = qname.substring(0, qname.length()-1);
        
        short qType = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 

        short qClass = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
        
        if(verboseTracing){
            System.out.println("Response ID: " + queryId + " Authoritative = " + (authoritativeAns == 1 && aCount > 0));
        }
        
    }
    
    
    // Recurisvely reads the resource records from the DNS response.
    // The message will either by compressed or un-compressed so this function calls the
    // method corresponding to that
    private static void ReadResponseResourceRecord (DNSNode node, byte[] response){
        
        short msgCompression = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 

        if(msgCompression == 0x0 || (originalNode.getType() == RecordType.getByCode(5) && foundCname))
            return;
        
            
        Short compressionCheck = new Short("16383");

        if((compressionCheck | msgCompression) == -1)
            CompressedMsg(node, msgCompression, response);
        else
            UnCompressedMessage(node, response);

        ReadResponseResourceRecord(node, response);        
    }
    
    
    
    // Method to read resource records that use compression
    // Adds the read resource records to the cache
    private static void CompressedMsg(DNSNode node, short msgCompression, byte[] response){
        
        Short compressionCheck = new Short("16383");
        
        String name = GetPointerValue(response, msgCompression);

        short type = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
            
        short rrclass = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
                
        int ttl = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                  ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));

        short rdLength = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
        
        String rdata = "";

        ResourceRecord rr;
        
        switch (type) {
            case 1: // A
                for (int i = 0; i < rdLength; i++ ) {

                    rdata += String.format("%d", (response[pos++] & 0xFF));
                    if(i != rdLength-1)
                        rdata += ".";   
                }
                
                try{
                    InetAddress address = InetAddress.getByName(rdata);
                    if(name.equals(node.getHostName()) && !serverCname){
                        resultIsQueryIp = true;
                        rr = new ResourceRecord(originalNode.getHostName(), RecordType.getByCode(type), ttl, address);
                        cache.addResult(rr);
                    } 
                    
                    rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, address);
                    cache.addResult(rr);
                    resourceRecords.add(rr);
                    return;

                } catch (Exception e) {
                    
                }
                break;
            case 2: // NS  
            
                byte octetLength = 0;

                while ((octetLength = response[pos++]) != 0x0) {
                    
                    if((compressionCheck | octetLength) == -1){
                        rdata += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF)));
                        rdata += ".";
                        break;
                    }
                                        
                    for (int i = 0; i < octetLength; i++) 
                        rdata += (char)response[pos++];
                    
                    rdata += ".";  
                }

                rdata = rdata.substring(0, rdata.length()-1);
                break;
            case 5: // CNAME
                for (int i = 0; i < rdLength; i++ ) {
                    
                    octetLength = response[pos++];
                    if(octetLength == 0)
                        break;

                    if((octetLength & 0xFF) == 0xc0){
                        rdata += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF))) + ".";
                        break;
                    }
                        
                    for (int n = 0; n < octetLength; n++) 
                        rdata += (char)response[pos++];
                
                    rdata += ".";  
                    foundCname = true;
                }
                
                rdata = rdata.substring(0, rdata.length()-1);                                           
                break;
                
            case 6:
                
                SOAServer(response);
                rdata = "----";
                break;
                
            case 15:

                rdata += (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)) + " "; 
                
                for (int i = 0; i < rdLength; i++ ) {
                    
                    octetLength = response[pos++];
                    if(octetLength == 0)
                        break;

                    if((octetLength & 0xFF) == 0xc0){
                        rdata += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF))) + ".";
                        break;
                    }
                        
                    for (int n = 0; n < octetLength; n++) 
                        rdata += (char)response[pos++];
                
                    rdata += ".";  
                    foundMX = true;
                }
                
                rdata = rdata.substring(0, rdata.length()-1);
                foundMX = true;
                break;
                
            case 28:
            
                byte[] ipv6bytes = new byte[rdLength];
                for(int i = 0; i < rdLength; i++)
                    ipv6bytes[i] = response[pos++];
                           
                for(int i = 0; i < rdLength; i += 2){
                    short ipv6Seg = (short)(((ipv6bytes[i]) << 8) | (ipv6bytes[i+1] & 0xFF));

                    String rdataSeg = String.format("%x", ipv6Seg);
                    rdata += rdataSeg + ":";
                }
                
                rdata = rdata.substring(0, rdata.length()-1);
                
                if(name.equals(node.getHostName()) && !serverCname){
                    resultIsQueryIp = true;
                    rr = new ResourceRecord(originalNode.getHostName(), RecordType.getByCode(type), ttl, rdata);
                    cache.addResult(rr);

                } 
                rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, rdata);
                cache.addResult(rr);
                resourceRecords.add(rr);
                return;

            default:
                pos += rdLength;
                rdata = "0.0.0.0";
                break;
        }
        
        rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, rdata);
        resourceRecords.add(rr);
        cache.addResult(rr);
    }
    
    
    // Method to read resource records that do not use compression
    // Adds the read resource records to the cache
    private static void UnCompressedMessage(DNSNode node, byte[] response){
        
        pos -= 2;
        int octetLength = 0;
        String name = "";
        while ((octetLength = response[pos++]) != 0) {

            for (int i = 0; i < octetLength; i++) 
                name += (char)response[pos++];
        
            name += ".";  
        }
        if(name.length() == 0)
            name = ".";
        else
            name = name.substring(0, name.length()-1);
                    
        short type = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 

        short rrclass = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
                        
        int ttl = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));
        
        short rdLength = (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)); 
        
        String rdata = "";

        ResourceRecord rr;
        
        switch (type) {
            case 1: // A
            
                for (int i = 0; i < rdLength; i++ ) {

                    rdata += String.format("%d", (response[pos++] & 0xFF));
                    if(i != rdLength-1)
                        rdata += ".";   
                }
                
                try{
                    InetAddress address = InetAddress.getByName(rdata);
                    if(name.equals(node.getHostName()) && !serverCname){
                        resultIsQueryIp = true;
                        rr = new ResourceRecord(originalNode.getHostName(), RecordType.getByCode(type), ttl, address);
                        cache.addResult(rr);
                    } 
                    
                    rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, address);
                    cache.addResult(rr);
                    resourceRecords.add(rr);
                    return;

                } catch (Exception e) {
                    
                }
                break;
                
            case 2: // NS 

                octetLength = 0;

                while ((octetLength = response[pos++]) != 0x0) {
                                        
                    for (int i = 0; i < octetLength; i++) 
                        rdata += (char)response[pos++];
                    
                    rdata += ".";  
                }

                rdata = rdata.substring(0, rdata.length()-1);
                break;

            case 5:
                
                for (int i = 0; i < rdLength; i++ ) {
                        
                        octetLength = response[pos++];
                        if(octetLength == 0)
                            break;
                            
                        for (int n = 0; n < octetLength; n++) 
                            rdata += (char)response[pos++];
                    
                        rdata += ".";  
                    }
                    
                    rdata = rdata.substring(0, rdata.length()-1);    
                    foundCname = true;
                    break;

            case 6:
                SOAServer(response);
                rdata = "----";
                break;
                
            case 15:
                rdata += (short)(((response[pos++]) << 8) | (response[pos++] & 0xFF)) + " "; 
                
                for (int i = 0; i < rdLength; i++ ) {
                    
                    octetLength = response[pos++];
                    if(octetLength == 0)
                        break;

                    if((octetLength & 0xFF) == 0xc0){
                        rdata += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF))) + ".";
                        break;
                    }
                        
                    for (int n = 0; n < octetLength; n++) 
                        rdata += (char)response[pos++];
                
                    rdata += ".";  
                    foundMX = true;
                }
                
                rdata = rdata.substring(0, rdata.length()-1);
                foundMX = true;
                break;
                
            case 28:
            
                byte[] ipv6bytes = new byte[rdLength];
                for(int i = 0; i < rdLength; i++)
                    ipv6bytes[i] = response[pos++];
                           
                for(int i = 0; i < rdLength; i += 2){
                    short ipv6Seg = (short)(((ipv6bytes[i]) << 8) | (ipv6bytes[i+1] & 0xFF));

                    String rdataSeg = String.format("%x", ipv6Seg);
                    rdata += rdataSeg + ":";
                }
                
                rdata = rdata.substring(0, rdata.length()-1);
                if(name.equals(node.getHostName()) && !serverCname){
                    resultIsQueryIp = true;
                    rr = new ResourceRecord(originalNode.getHostName(), RecordType.getByCode(type), ttl, rdata);
                    cache.addResult(rr);

                } 
                rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, rdata);
                cache.addResult(rr);
                resourceRecords.add(rr);
                return;
                
            default:
                pos += rdLength;
                rdata = "0.0.0.0";
                break;
                
            }
            
        rr = new ResourceRecord(name, RecordType.getByCode(type), ttl, rdata);
        resourceRecords.add(rr);
        cache.addResult(rr);
    }

    
    
    // Method to skip over resource records that are SOA servers (type 6)
    private static void SOAServer(byte[] response) {
        int octetLength = 0;

        String mname = "";
        while ((octetLength = response[pos++]) != 0) {
            
            if((octetLength & 0xFF) == 0xc0){
                mname += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF))) + ".";
                break;
            }

            for (int i = 0; i < octetLength; i++) 
                mname += (char)response[pos++];
        
            mname += ".";  
        }
        if(mname.length() == 0)
            mname = ".";
        else
            mname = mname.substring(0, mname.length()-1);
                    
        octetLength = 0;
        String rname = "";
        while ((octetLength = response[pos++]) != 0) {
            
            if((octetLength & 0xFF) == 0xc0){
                rname += GetPointerValue(response, (short)((octetLength << 8) | (response[pos++] & 0xFF))) + ".";
                break;
            }
            for (int i = 0; i < octetLength; i++) 
                rname += (char)response[pos++];
        
            rname += ".";  
        }
        if(rname.length() == 0)
            rname = ".";
        else
            rname = rname.substring(0, rname.length()-1);
                    
        long serial = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF)) & 0x00000000FFFFFFFF;

        int refresh = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));
        
        int retry = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));
        
        int expire = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));
        
        int minimum = (((response[pos++] << 24) & 0xFF000000) | ((response[pos++] << 16) & 0x00FF0000) |
                            ((response[pos++] << 8) & 0x0000FF00) | (response[pos++] & 0x000000FF));
    }



    // Returns the value of a pointer in the resource record
    private static String GetPointerValue(byte[] response, short msgCompression){
        
        Short compressionCheck = new Short("16383");
        int pointerPos = msgCompression & 0x3fff;
        int octetLength = 0;
        String data = "";
        
        while ((octetLength = response[pointerPos++]) != 0x0) {
            
            if((compressionCheck | octetLength) == -1){
                data += GetPointerValue(response, (short)((octetLength << 8) | (response[pointerPos++] & 0xFF)));
                data += ".";
                break;
            }

            for (int i = 0; i < octetLength; i++) 
                data += (char)response[pointerPos++];
             
            data += ".";  
        }
        
        return data.substring(0, data.length() - 1);
    }
    
    
    
    // Method to print records when verbose tracing is on
    private static void verbosePrintResourceRecord(DNSNode node){
        
        Set<String> cnames = new HashSet<String>();
        for(ResourceRecord record : resourceRecords){
            if(record.getType() == RecordType.getByCode(5))
                cnames.add(record.getTextResult());
        }
        
        Set<ResourceRecord> answerResourceRecords = new HashSet<ResourceRecord>();
        Set<ResourceRecord> nsResourceRecords = new HashSet<ResourceRecord>();
        Set<ResourceRecord> additionalResourceRecords = new HashSet<ResourceRecord>();

        for (ResourceRecord record : resourceRecords) {
            if((record.getType() == RecordType.getByCode(5) || (record.getHostName().equals(node.getHostName()) && record.getType() == node.getType()) ||
            cnames.contains(record.getHostName())) && !serverCnames.contains(record.getHostName()))
                answerResourceRecords.add(record);
            else if(record.getType() == RecordType.getByCode(2) || record.getType() == RecordType.getByCode(6))
                nsResourceRecords.add(record);
            else if(!record.getHostName().equals(node.getHostName())){
                additionalResourceRecords.add(record);
            }
        } 
        
        System.out.println("  Answers (" + answerResourceRecords.size() + ")");
        for (ResourceRecord record : answerResourceRecords) {
            System.out.printf("       %-30s %-10d %-4s %s\n", record.getHostName(),
                record.getTTL(), record.getType(), record.getTextResult()); 
        } 

        System.out.println("  Nameservers (" + nsResourceRecords.size() + ")");
        for (ResourceRecord record : nsResourceRecords) {
                System.out.printf("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(), record.getType(), record.getTextResult()); 
        } 
        
        System.out.println("  Additional Information (" + additionalResourceRecords.size() + ")");
        for (ResourceRecord record : additionalResourceRecords) {
                System.out.printf("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(), record.getType(), record.getTextResult()); 
        }
    }
    

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {

        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), record.getTTL(), record.getTextResult());
        }
    }
}
