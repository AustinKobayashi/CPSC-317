cmake_minimum_required(VERSION 3.10)
project(a3_l4l0b C)

set(CMAKE_C_STANDARD 11)

include_directories(.)

add_executable(a3_l4l0b
        cdupcommand.c
        cdupcommand.h
        CSftp.c
        cwdcommand.c
        cwdcommand.h
        dir.c
        dir.h
        modecommand.c
        modecommand.h
        strucommand.c
        strucommand.h
        typecommand.c
        typecommand.h
        usage.c
        usage.h
        usercommand.c
        usercommand.h
        write.c
        write.h)
