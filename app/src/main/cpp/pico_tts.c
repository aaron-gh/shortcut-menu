/*
 * The JNI for PicoTts: opens SVOX Pico with one voice, and turns text into 16 kHz, 16-bit mono
 * samples. Modelled on AOSP's com_svox_picottsengine.cpp.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "picoapi.h"

#define MEM_SIZE 2500000
#define VOICE_NAME "voice"
#define OUT_BUF_SIZE 128

typedef struct {
  void *memory;
  pico_System system;
  pico_Resource ta;
  pico_Resource sg;
  pico_Engine engine;
} Pico;

static void closePico(Pico *pico) {
  if (pico->system) {
    if (pico->engine) {
      pico_disposeEngine(pico->system, &pico->engine);
    }
    pico_releaseVoiceDefinition(pico->system, (const pico_Char *) VOICE_NAME);
    if (pico->sg) {
      pico_unloadResource(pico->system, &pico->sg);
    }
    if (pico->ta) {
      pico_unloadResource(pico->system, &pico->ta);
    }
    pico_terminate(&pico->system);
  }
  free(pico->memory);
  free(pico);
}

static int loadResource(Pico *pico, JNIEnv *env, jstring path, pico_Resource *resource) {
  const char *chars = (*env)->GetStringUTFChars(env, path, NULL);
  pico_Status status = pico_loadResource(pico->system, (const pico_Char *) chars, resource);
  (*env)->ReleaseStringUTFChars(env, path, chars);
  if (status != PICO_OK) {
    return 0;
  }
  pico_Retstring name;
  return pico_getResourceName(pico->system, *resource, name) == PICO_OK &&
         pico_addResourceToVoiceDefinition(
           pico->system, (const pico_Char *) VOICE_NAME, (const pico_Char *) name) == PICO_OK;
}

JNIEXPORT jlong JNICALL
Java_io_github_aaron_1gh_shortcutmenu_PicoTts_nativeOpen(
  JNIEnv *env, jclass clazz, jstring taPath, jstring sgPath) {
  Pico *pico = calloc(1, sizeof(Pico));
  if (!pico) {
    return 0;
  }
  pico->memory = malloc(MEM_SIZE);
  if (!pico->memory ||
      pico_initialize(pico->memory, MEM_SIZE, &pico->system) != PICO_OK) {
    pico->system = NULL;
    closePico(pico);
    return 0;
  }
  if (pico_createVoiceDefinition(pico->system, (const pico_Char *) VOICE_NAME) != PICO_OK ||
      !loadResource(pico, env, taPath, &pico->ta) ||
      !loadResource(pico, env, sgPath, &pico->sg) ||
      pico_newEngine(pico->system, (const pico_Char *) VOICE_NAME, &pico->engine) != PICO_OK) {
    closePico(pico);
    return 0;
  }
  return (jlong) (intptr_t) pico;
}

JNIEXPORT jshortArray JNICALL
Java_io_github_aaron_1gh_shortcutmenu_PicoTts_nativeSynthesize(
  JNIEnv *env, jclass clazz, jlong handle, jstring text) {
  Pico *pico = (Pico *) (intptr_t) handle;
  const char *chars = (*env)->GetStringUTFChars(env, text, NULL);
  if (strlen(chars) >= PICO_INT16_MAX) {
    (*env)->ReleaseStringUTFChars(env, text, chars);
    return NULL;
  }
  // The NUL is sent too, which tells Pico that the text is complete.
  pico_Int16 remaining = (pico_Int16) (strlen(chars) + 1);
  const pico_Char *input = (const pico_Char *) chars;

  size_t capacity = 16000;
  size_t used = 0;
  short *samples = malloc(capacity * sizeof(short));
  int failed = samples == NULL;
  short out[OUT_BUF_SIZE / 2];
  while (!failed && remaining > 0) {
    pico_Int16 sent = 0;
    if (pico_putTextUtf8(pico->engine, input, remaining, &sent) != PICO_OK) {
      failed = 1;
      break;
    }
    remaining -= sent;
    input += sent;
    pico_Status status;
    do {
      pico_Int16 received = 0;
      pico_Int16 type = 0;
      status = pico_getData(pico->engine, out, OUT_BUF_SIZE, &received, &type);
      size_t count = (size_t) received / sizeof(short);
      if (used + count > capacity) {
        capacity *= 2;
        short *grown = realloc(samples, capacity * sizeof(short));
        if (!grown) {
          failed = 1;
          break;
        }
        samples = grown;
      }
      memcpy(samples + used, out, count * sizeof(short));
      used += count;
    } while (status == PICO_STEP_BUSY);
    if (!failed && status != PICO_STEP_IDLE) {
      failed = 1;
    }
  }
  (*env)->ReleaseStringUTFChars(env, text, chars);
  if (failed) {
    pico_resetEngine(pico->engine, PICO_RESET_SOFT);
    free(samples);
    return NULL;
  }
  jshortArray result = (*env)->NewShortArray(env, (jsize) used);
  if (result) {
    (*env)->SetShortArrayRegion(env, result, 0, (jsize) used, samples);
  }
  free(samples);
  return result;
}

JNIEXPORT void JNICALL
Java_io_github_aaron_1gh_shortcutmenu_PicoTts_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
  closePico((Pico *) (intptr_t) handle);
}
