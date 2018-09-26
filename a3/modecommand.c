#include <stdio.h>
#include <ctype.h>
#include <memory.h>
#include "write.h"
#include "modecommand.h"

void ModeCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex) {

    if (firstSpaceIndex == -1 || len != 8) {
        char *reply = "501 Syntax error in parameters or arguments.\r\n";
        Write(connectionFd, reply, strlen(reply));

    } else {

        char type = (char) toupper(buf[len - 3]);

        if (type == 83) {
            char *reply = "200 Mode set to S.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else if (type == 66) {
            char *reply = "504 Mode B not implemented.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else if (type == 67) {
            char *reply = "504 Mode C not implemented.\r\n";
            Write(connectionFd, reply, strlen(reply));
        } else {
            char *reply = "501 Syntax error in parameters or arguments.\r\n";
            Write(connectionFd, reply, strlen(reply));
        }
    }
}