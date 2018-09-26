#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "write.h"
#include "cwdcommand.h"

int ValidCwd(char *path, int len);


void CWDCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex) {

    if (firstSpaceIndex == -1) {
        char *reply = "501 Syntax error in parameters or arguments.\r\n";
        Write(connectionFd, reply, strlen(reply));

    } else {

        char *path = malloc(sizeof(char) * (len - 6));

        int i;

        for (i = 0; i < len - 6; i++)
            path[i] = buf[i + firstSpaceIndex + 1];

        int valid = ValidCwd(path, len - 6);

        if (valid == 0) {
            char *reply = "550 Failed to change directory.\r\n";
            Write(connectionFd, reply, strlen(reply));
            free(path);
            return;
        }


        if (chdir(path) == -1) {
            char *reply = "550 Failed to change directory.\r\n";
            Write(connectionFd, reply, strlen(reply));

        } else {
            char *reply = "250 CWD command successful.\r\n";
            Write(connectionFd, reply, strlen(reply));
        }

        free(path);
    }
}


int ValidCwd(char *path, int len) {

    if (len > 1 && path[0] == 46 && path[1] == 47) // starts with ./
        return 0;

    if (len > 2 && path[0] == 46 && path[1] == 46 && path[2] == 47) // starts with ../
        return 0;

    if (len > 2 && strstr(path, "../") != NULL) // contains ../
        return 0;

    if (len == 1 && path[0] == 46) // path = .
        return 0;

    if (len == 2 && path[0] == 46 && path[1] == 46) // path = ..
        return 0;

    if (len == 1 && path[0] == 47) // path = /
        return 0;

    if (len == 2 && path[0] == 47 && path[1] == 47) // path = //
        return 0;

    return 1;
}