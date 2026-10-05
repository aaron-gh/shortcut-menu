cmake_minimum_required(VERSION 3.22.1)
project(picotts C)

# SVOX Pico, from AOSP, and the JNI that the app's PicoSpeaker uses to speak with it.
file(GLOB PICO_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/pico/*.c)
add_library(picotts SHARED pico_tts.c ${PICO_SOURCES})
target_include_directories(picotts PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/pico)
# Pico is third-party code, built as it is.
set_source_files_properties(${PICO_SOURCES} PROPERTIES COMPILE_OPTIONS -w)
