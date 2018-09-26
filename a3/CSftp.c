#include <sys/types.h>
#include <sys/socket.h>
#include <stdio.h>
#include "dir.h"
#include "usage.h"
#include <stdlib.h>
#include <netinet/in.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <ctype.h>
#include <arpa/inet.h>
#include "usercommand.h"
#include "write.h"
#include "cwdcommand.h"
#include "cdupcommand.h"
#include "typecommand.h"
#include "modecommand.h"
#include "strucommand.h"


int loggedIn = 0;
int connected = 0;
char *startDir;
size_t startDirSize;
struct sockaddr_in acceptedSocket;
int dataFd = 0;
int pasvSocketFd = 0;

void RecieveClientInput(int connectionFd);

void LogInSequence(int connectionFd);

void ParseCommand(int connectionFd, char buf[1500], int len);

void PASVCommand(int connectionFd, int len);

void WritePasvCommandMsg(int connectionFd);

void NLSTCommand(int connectionFd, int len);

void RETRCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex);

// Here is an example of how to use the above function. It also shows
// one how to get the arguments passed on the command line.

int main(int argc, char **argv) {

	char* buf = calloc(8196, sizeof(char));
    buf = getcwd(buf, 8196);
    startDirSize = strlen(buf);
    startDir = malloc(sizeof(char) * startDirSize);
    strncpy(startDir, buf, startDirSize);
    printf("start dir %s\n", startDir);

	free(buf);


	if (argc != 2) {
		usage(argv[0]);
		return -1;
	}

	int listenSocketFd = 0, connectionFd = 0;
	int port = (int) strtol(argv[1], NULL, 10);
	printf("port: %d\r\n", port);

	struct sockaddr_in serv_addr;

	if ((listenSocketFd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
		perror("socket failed");
		exit(EXIT_FAILURE);
	}

	memset(&serv_addr, '0', sizeof(serv_addr));

	serv_addr.sin_family = AF_INET;
	serv_addr.sin_addr.s_addr = INADDR_ANY;
	serv_addr.sin_port = htons(port);

	if ((bind(listenSocketFd, (struct sockaddr *) &serv_addr, sizeof(serv_addr))) < 0) {
		perror("bind failed");
		exit(EXIT_FAILURE);
	}

	listen(listenSocketFd, 1);
	connectionFd = accept(listenSocketFd, (struct sockaddr *) NULL, NULL);
	connected++;

	socklen_t len = sizeof(struct sockaddr);

	getsockname(connectionFd, (struct sockaddr *) &acceptedSocket, &len);

	LogInSequence(connectionFd);

	while (1) {

		if (connected == 0) {
			connectionFd = accept(listenSocketFd, (struct sockaddr *) NULL, NULL);
			connected++;
			LogInSequence(connectionFd);
		} else
			RecieveClientInput(connectionFd);
	}
}


// Recieves the client input and stores it in a buffer
void RecieveClientInput(int connectionFd) {
	fd_set readset;
	char readBuf[1500]; //may need to dynamically alloc this
    memset(readBuf, 0, sizeof(char) * 1500);
	int result;
	int len = 0;

	FD_ZERO(&readset);
	FD_SET(connectionFd, &readset);
	result = select(connectionFd + 1, &readset, NULL, NULL, NULL);

	while (result == -1)
		result = select(connectionFd + 1, &readset, NULL, NULL, NULL);

	if (result > 0) {
		if (FD_ISSET(connectionFd, &readset)) {

			ioctl(connectionFd, FIONREAD, &len);
			if (len > 0) {
				len = (int) read(connectionFd, readBuf, len);
			} else {
				loggedIn = 0;
				connected = 0;
				close(connectionFd);
				return;
			}
		}
	}

	printf("readbuf: %s\r\n", readBuf);
	ParseCommand(connectionFd, readBuf, len);
}


// Parses the client input and calls the appropriate function
void ParseCommand(int connectionFd, char buf[1500], int len) {

	int firstSpaceIndex = -1;
	int i;
	for (i = 0; i < len; i++) {
		if (buf[i] == 32) {
			firstSpaceIndex = i;
			break;
		}
	}

	char *cmd;

	if (firstSpaceIndex != -1) {
		cmd = malloc(sizeof(char) * (firstSpaceIndex + 1));
		for (i = 0; i <= firstSpaceIndex; i++)
			cmd[i] = (char) toupper(buf[i]);
	} else {
		cmd = malloc(sizeof(char) * len);
		for (i = 0; i < len; i++)
			cmd[i] = (char) toupper(buf[i]);

	}


	if ((strncmp(cmd, "USER", 6) == 0 && len == 6) || strncmp(cmd, "USER ", 5) == 0) {
		loggedIn = UserCommand(connectionFd, buf, len, firstSpaceIndex, loggedIn);

	} else if ((strncmp(cmd, "QUIT", 4) == 0 && len == 6) || ((strncmp(cmd, "QUIT ", 5) == 0) && len == 7)) {
	    char* reply = "221 Goodbye.\r\n";
		Write(connectionFd, reply, strlen(reply));
		loggedIn = 0;
		connected = 0;
		close(connectionFd);
		free(cmd);
		return;

	} else if (loggedIn == 0) {
	    char* reply = "530 Please login with USER and PASS.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else if ((strncmp(cmd, "CWD", 3) == 0 && len == 5) || strncmp(cmd, "CWD ", 4) == 0) {
		CWDCommand(connectionFd, buf, len, firstSpaceIndex);

	} else if ((strncmp(cmd, "CDUP", 4) == 0 && len == 6) || (strncmp(cmd, "CDUP ", 5) == 0 && len == 7)) {
		CDUPCommand(connectionFd, startDir);

	} else if ((strncmp(cmd, "TYPE", 4) == 0 && len == 6) || strncmp(cmd, "TYPE ", 5) == 0) {
		TypeCommand(connectionFd, buf, len, firstSpaceIndex);

	} else if ((strncmp(cmd, "MODE", 4) == 0 && len == 6) || strncmp(cmd, "MODE ", 5) == 0) {
		ModeCommand(connectionFd, buf, len, firstSpaceIndex);

	} else if ((strncmp(cmd, "STRU", 4) == 0 && len == 6) || strncmp(cmd, "STRU ", 5) == 0) {
		STRUCommand(connectionFd, buf, len, firstSpaceIndex);

	} else if ((strncmp(cmd, "PASV", 4) == 0 && len == 6) || strncmp(cmd, "PASV ", 5) == 0) {
		PASVCommand(connectionFd, len);

	} else if ((strncmp(cmd, "NLST", 4) == 0 && len == 6) || strncmp(cmd, "NLST ", 5) == 0) {
		NLSTCommand(connectionFd, len);

	} else if ((strncmp(cmd, "RETR", 4) == 0 && len == 6) || strncmp(cmd, "RETR ", 5) == 0) {
		RETRCommand(connectionFd, buf, len, firstSpaceIndex);

	} else {
	    char* reply = "500 Unknown command.\r\n";// Unknown command.\r\n
		Write(connectionFd, reply, strlen(reply));
	}

	free(cmd);
}


void PASVCommand(int connectionFd, int len) {

	if (len > 7) {
	    char* reply = "501 Syntax error in parameters or arguments.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else {

		if(pasvSocketFd != 0)
			close(pasvSocketFd);

		struct sockaddr_in pasv_addr;

		if ((pasvSocketFd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
			perror("socket failed");
			exit(EXIT_FAILURE);
		}

		memset(&pasv_addr, '0', sizeof(pasv_addr));

		pasv_addr.sin_family = acceptedSocket.sin_family;
		pasv_addr.sin_addr.s_addr = acceptedSocket.sin_addr.s_addr;
		pasv_addr.sin_port = 0;


		if ((bind(pasvSocketFd, (struct sockaddr *) &pasv_addr, sizeof(pasv_addr))) < 0) {
			perror("bind failed");
			exit(EXIT_FAILURE);
		}

		listen(pasvSocketFd, 1);

		WritePasvCommandMsg(connectionFd);

	}
}


void WritePasvCommandMsg(int connectionFd) {

	socklen_t pasvLen = sizeof(struct sockaddr);
	struct sockaddr_in pasvSocket;
	getsockname(pasvSocketFd, (struct sockaddr *) &pasvSocket, &pasvLen);
    int fullport = ntohs(pasvSocket.sin_port);
	printf("port: %d\r\n", fullport);
    int port0 = (fullport >> 8) & 0xff;
    int port1 = fullport & 0xff;


	char buf[55];

    int test = acceptedSocket.sin_addr.s_addr;
    sprintf(buf,"227 Entering Passive Mode (%d,%d,%d,%d,%d,%d).\r\n",test & 0xff,(test >> 8) & 0xff,
            (test >> 16) & 0xff,(test >> 24) & 0xff,port0,port1);


    Write(connectionFd, buf, strlen(buf));
}


void NLSTCommand(int connectionFd, int len) {

	if (len > 7) {
	    char* reply = "501 Syntax error in parameters or arguments.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else if (pasvSocketFd == 0) {
		char* reply = "425 Use PASV first.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else {

		char* directory;
		char* buf = calloc(8196, sizeof(char));
        buf = getcwd(buf, 8196);
        int directorySize = strlen(buf);
        directory = malloc(sizeof(char) * directorySize);
        memcpy(directory, buf, directorySize);

		if (dataFd == 0) {
			fd_set rfds;

			int retval;
			struct timeval tv;
			tv.tv_sec = 10;

			FD_ZERO(&rfds);
			FD_SET(pasvSocketFd, &rfds);

            retval = select(pasvSocketFd + 1, &rfds, &rfds, NULL, &tv);

			if (retval) {
			    char* reply = "150 Opening data connection.\r\n";
				Write(connectionFd, reply, strlen(reply));
				dataFd = accept(pasvSocketFd, (struct sockaddr *) NULL, NULL);

			} else {
			    char* reply = "425 Can't open data connection.\r\n";
				Write(connectionFd, reply, strlen(reply));
				close(dataFd);
				close(pasvSocketFd);
				dataFd = 0;
				pasvSocketFd = 0;
				free(buf);
				free(directory);
				return;
			}
		} else {
		    char* reply = "125 Data connection already open; transfer starting.\r\n";
			Write(connectionFd, reply, strlen(reply));
		}

		int ret = listFiles(dataFd, directory);

		if (ret == -1) {
		    char* reply = "450 Requested file action not taken.\r\n";
			Write(connectionFd, reply, strlen(reply));

		} else if (ret == -2) {
		    char* reply = "451 Requested action aborted. Local error in processing.\r\n";
			Write(connectionFd, reply, strlen(reply));

		} else {
		    char* reply = "226  Directory send OK.\r\n";
			Write(connectionFd, reply, strlen(reply));

		}

		close(dataFd);
        close(pasvSocketFd);
		dataFd = 0;
		pasvSocketFd = 0;
		free(buf);
        free(directory);
    }
}


void RETRCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex) {

	if (firstSpaceIndex == -1) {
	    char* reply = "501 Syntax error in parameters or arguments.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else if (pasvSocketFd == 0) {
		char* reply = "425 Use PASV first.\r\n";
		Write(connectionFd, reply, strlen(reply));

	} else {

		if (dataFd == 0) {
			fd_set rfds;

			int retval;
			struct timeval tv;
			tv.tv_sec = 10;

			FD_ZERO(&rfds);
			FD_SET(pasvSocketFd, &rfds);

			retval = select(pasvSocketFd + 1, &rfds, &rfds, NULL, &tv);

			if (retval) {
				char* reply = "150 Opening data connection.\r\n";
				Write(connectionFd, reply, strlen(reply));
				dataFd = accept(pasvSocketFd, (struct sockaddr *) NULL, NULL);

			} else {
				char* reply = "425 Can't open data connection.\r\n";
				Write(connectionFd, reply, strlen(reply));
                close(dataFd);
                close(pasvSocketFd);
                dataFd = 0;
                pasvSocketFd = 0;
				return;
			}
		} else {
			char* reply = "125 Data connection already open; transfer starting.\r\n";
			Write(connectionFd, reply, strlen(reply));
		}

		printf("len: %d\n", len);
		char *fileName = calloc((size_t) (len - 7), sizeof(char));
        long fileLength;
        char *fileBuf = 0;

        memset(fileName, 0, sizeof(char) * (len - 7));
        memcpy(fileName, &buf[5], sizeof(char) * (len - 7));

        printf("len %d\n", len);
        printf("buf len %lu\n", strlen(buf));
        printf("file len %lu\n", strlen(fileName));
        printf("file %s\n", fileName);
        printf("buf %s\n", buf);
		FILE *file = fopen(fileName, "r");
        int freeFileBuf = 0;
		if (file != NULL) {
			fseek(file, 0, SEEK_END);
			fileLength = ftell(file);
			fseek(file, 0, SEEK_SET);
			fileBuf = malloc(fileLength);
			if (fileBuf) {
				fread(fileBuf, 1, fileLength, file);
				freeFileBuf = 1;
			}
			fclose(file);
		} else {
			char* reply = "450 Requested file action not taken.\r\n";
			Write(connectionFd, reply, strlen(reply));
            close(dataFd);
            close(pasvSocketFd);
            dataFd = 0;
            pasvSocketFd = 0;
            free(fileName);
			return;
		}


		if (fileBuf) {

			Write(dataFd, fileBuf, fileLength);
			char* reply = "226 Requested file action okay, completed.\r\n";
            Write(connectionFd, reply, strlen(reply));
		} else {
			char* reply = "451 Requested action aborted. Local error in processing.\r\n";
			Write(connectionFd, reply, strlen(reply));
		}

        close(dataFd);
        close(pasvSocketFd);
        dataFd = 0;
        pasvSocketFd = 0;
		free(fileName);
        if(freeFileBuf)
            free(fileBuf);
	}
}


void LogInSequence(int connectionFd) {

	char* reply = "220\r\n";
	Write(connectionFd, reply, strlen(reply));

	RecieveClientInput(connectionFd);
}


void error(char *msg) {
	perror(msg);
	exit(1);
}
