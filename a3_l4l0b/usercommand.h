#ifndef _usercommand__

#define _usercommand__

int UserCommand(int connectionFd, char buf[1500], int len, int firstSpaceIndex, int loggedIn);

#endif
