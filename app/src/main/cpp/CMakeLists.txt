cmake_minimum_required(VERSION 3.22.1)
project(keymapper CXX)

add_library(keymapper SHARED evdev.cpp)
target_compile_options(keymapper PRIVATE -Wall -Wextra -O2)
