#include <stdio.h>
#include <ctype.h>
#include <memory.h>
#include "write.h"
#include "typecommand.h"

void TypeCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex) {

    if (firstSpaceIndex == -1 || len < 5) {
        char *reply = "501 Syntax error in parameters or arguments.\r\n";
        Write(connectionFd, reply, strlen(reply));

    } else {

        char type = (char) toupper(buf[5]);
        char secondParam = -1;
        if (len == 10)
            secondParam = (char) toupper(buf[7]);

        if (secondParam == -1) {
            if (type == 65) { // a
                char *reply = "200 Switching to ASCII mode.\r\n";
                Write(connectionFd, reply, strlen(reply));

            } else if (type == 73) { // i
                char *reply = "200 Switching to Binary mode.\r\n";
                Write(connectionFd, reply, strlen(reply));

            } else if (type == 69) { // e
                char *reply = "504 EBCDIC not implemented.\r\n";
                Write(connectionFd, reply, strlen(reply));
            } else if (type == 76) { // L
                char *reply = "504 Local byte not implemented.\r\n";
                Write(connectionFd, reply, strlen(reply));
            } else {
                char *reply = "501 Syntax error in parameters or arguments.\r\n";
                Write(connectionFd, reply, strlen(reply));
            }
        } else {
            if (secondParam == 78 || secondParam == 67 || secondParam == 84) {
                char *reply = "504 TYPE with second parameters not implemented.\r\n";
                Write(connectionFd, reply, strlen(reply));

            } else {
                char *reply = "501 Unrecognised TYPE command.\r\n";
                Write(connectionFd, reply, strlen(reply));
            }
        }
    }
}