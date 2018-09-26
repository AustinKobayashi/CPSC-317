#include <stdio.h>
#include <unistd.h>
#include "write.h"

void Write(int fileDescriptor, char *msg, size_t len) {

    int n = write(fileDescriptor, msg, len);
    if (n < 0) {
        printf("n < 0 \n");
        perror("ERROR writing to socket");
    }
}
