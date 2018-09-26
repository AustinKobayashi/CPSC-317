#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "write.h"
#include "cdupcommand.h"

void CDUPCommand(int connectionFd, char *startDir) {

    char *buf = calloc(8196, sizeof(char));
    buf = getcwd(buf, 8196);
    size_t size = strlen(buf);
    char *dir = malloc(sizeof(char) * size);
    strncpy(dir, buf, size);
    printf("start dir %s\n", dir);

    if (strncmp(dir, startDir, size) == 0) {
        char *reply = "550 Failed to change directory.\r\n";
        Write(connectionFd, reply, strlen(reply));
        free(buf);
        free(dir);
        return;
    }

    if (chdir("..") == -1) {
        char *reply = "550 Failed to change directory.\r\n";
        Write(connectionFd, reply, strlen(reply));

    } else {
        char *reply = "250 CWD command successful.\r\n";
        Write(connectionFd, reply, strlen(reply));
    }

    free(buf);
    free(dir);
}