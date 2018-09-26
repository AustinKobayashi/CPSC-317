#include <stdio.h>
#include <ctype.h>
#include <memory.h>
#include "write.h"
#include "strucommand.h"

void STRUCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex) {

    if (firstSpaceIndex == -1 || len != 8) {
        char *reply = "501 Syntax error in parameters or arguments.\r\n";
        Write(connectionFd, reply, strlen(reply));

    } else {

        char stru = (char) toupper(buf[5]);
        if (stru == 70) {
            char *reply = "200 Structure set to F.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else if (stru == 82) {
            char *reply = "504 Structure R not implemented.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else if (stru == 80) {
            char *reply = "504 Structure P not implemented.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else {
            char *reply = "501 Syntax error in parameters or arguments.\r\n";
            Write(connectionFd, reply, strlen(reply));
        }
    }
}