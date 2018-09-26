#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "usercommand.h"
#include "write.h"

int UserCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex, int loggedIn){
	
		if(firstSpaceIndex == -1){
			char* reply = "530 This FTP server is cs317 only.\r\n";
			Write(connectionFd, reply, strlen(reply));
			
		} else {
		
		char* userName = malloc(sizeof(char) * ((len - 2) - (firstSpaceIndex + 1)));
		
		int i;
		for(i = firstSpaceIndex + 1; i < len - 2; i++)
			userName[i - (firstSpaceIndex + 1)] = buf[i];
		
						
		if(strncmp (userName, "cs317", 5) == 0 && ((len - 2) - (firstSpaceIndex + 1)) == 5){
			char* reply = "230 Login successful.\r\n";
			Write(connectionFd, reply, strlen(reply));
			loggedIn = 1;
			free(userName);
			return loggedIn;
			
		} else {
			char* reply = "530 This FTP server is cs317 only.\r\n";
			Write(connectionFd, reply, strlen(reply));
		}
		
		free(userName);
	}
	
	return loggedIn;
}