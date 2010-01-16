#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <jni.h>
#include <jvmti.h>

#include "state.h"
#include "options.h"
#include "common.h"
#include "jnicheck.h"

/* function declarations. */
static void JNICALL bda_vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread);
static void JNICALL bda_vm_start(jvmtiEnv *jvmti, JNIEnv* env);
static void JNICALL bda_vm_bind(jvmtiEnv *jvmti, JNIEnv *env,
                                jthread thread, jmethodID method,
                                void* address, void** new_address_ptr);
static void JNICALL bda_vm_death(jvmtiEnv *jvmti, JNIEnv *env);
static void JNICALL bda_vm_data_dump_request(jvmtiEnv *jvmti);
static void JNICALL bda_vm_thread_start(jvmtiEnv *jvmti, JNIEnv * env, jthread t);
static void JNICALL bda_vm_thread_end(jvmtiEnv *jvmti, JNIEnv * env, jthread t);

/* function definitions. */
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
  jvmtiError err;
  jvmtiCapabilities capa;
  jvmtiEventCallbacks callbacks;
  jvmtiEnv *jvmti;
  int rc;
  unsigned int nthread;

  nthread = GET_NATIVE_THREADID();

  assert(bda_jvm == NULL && vm != NULL);
  bda_jvm = vm;

  /* Parse incoming options. */
  bda_parse_options(options);

  /* Wait for the debugger attach. */
#if defined(__GNUC__)
  if (bda_options.dbgwait) {
      int pid = GET_CURRENT_PROCESS_ID();
      printf("bda:wait: vm=%p pid=%d nthread = %x\n", vm, pid, nthread);
      while(bda_options.dbgwait) {
          sleep(1);
      }
  }
#endif

  /* Ensure JVMTI agent capabilities. */
  rc = (*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1);
  assert(rc == JNI_OK);
  err = (*jvmti)->GetCapabilities(jvmti, &capa);
  assert(err == JVMTI_ERROR_NONE);
  if (!bda_options.nointerpose) {
      if (!capa.can_generate_native_method_bind_events) {
          capa.can_generate_native_method_bind_events = 1;
          err = (*jvmti)->AddCapabilities(jvmti, &capa);
          assert(err == JVMTI_ERROR_NONE);
      }
      if (!capa.can_get_bytecodes) {
          capa.can_get_bytecodes = 1;
          err = (*jvmti)->AddCapabilities(jvmti, &capa);
          assert(err == JVMTI_ERROR_NONE);
      }
      if (!capa.can_get_line_numbers) {
          capa.can_get_line_numbers = 1;
          err = (*jvmti)->AddCapabilities(jvmti, &capa);
          assert(err == JVMTI_ERROR_NONE);
      }
  }

  /* Initialize the JVMTI event call backs. */
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMInit = &bda_vm_init;
  callbacks.VMStart = &bda_vm_start;
  callbacks.VMDeath = &bda_vm_death;
  callbacks.ThreadStart = &bda_vm_thread_start;
  callbacks.ThreadEnd = &bda_vm_thread_end;
  callbacks.DataDumpRequest = &bda_vm_data_dump_request;
  if (!bda_options.nointerpose) {
      callbacks.NativeMethodBind = &bda_vm_bind;
  }

  err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
  assert(err == JVMTI_ERROR_NONE);

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                           JVMTI_EVENT_VM_DEATH, NULL);
  assert(err == JVMTI_ERROR_NONE);

  if (!bda_options.nointerpose) {
      err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                               JVMTI_EVENT_NATIVE_METHOD_BIND, NULL);
      assert(err == JVMTI_ERROR_NONE);
  }

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                           JVMTI_EVENT_VM_INIT, NULL);
  assert(err == JVMTI_ERROR_NONE);

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                           JVMTI_EVENT_VM_START, NULL);
  assert(err == JVMTI_ERROR_NONE);

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                           JVMTI_EVENT_DATA_DUMP_REQUEST, NULL);
  assert(err == JVMTI_ERROR_NONE);

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                           JVMTI_EVENT_THREAD_START, NULL);
  assert(err == JVMTI_ERROR_NONE);

  err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, 
                                           JVMTI_EVENT_THREAD_END, NULL);
  assert(err == JVMTI_ERROR_NONE);

  bda_jvmti = jvmti;
  if (bda_options.verbose >= 1) {
      printf("bda:loaded: jvmti = %p nthread = %x\n", jvmti, nthread);
  }

  return 0;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm)
{
  if (bda_options.verbose >= 1 ) {
    printf("bda:unloaded: vm = %p\n", vm);
  }
}

static void JNICALL bda_vm_start(jvmtiEnv *jvmti, JNIEnv* env)
{
  unsigned int nthread;
  jvmtiError err;

  nthread = GET_NATIVE_THREADID();

  /* set c2j jni proxies. */
  err = (*jvmti)->GetJNIFunctionTable(jvmti, &bda_orig_jni_funcs);
  assert(err == JVMTI_ERROR_NONE);
  if (!bda_options.nointerpose) {
      bda_c2j_proxy_install(jvmti);
  }
  bda_jnicheck_init(env);

  /* obtain Java agent method identifiers. */
  bda_agent_init(env);

  /* enable deferred native proxies during pridomial phase. */
  if (!bda_options.nointerpose) {
      bda_j2c_proxy_deferred_methods_reregister(jvmti, env);
  }

  if (bda_options.verbose >= 1) {
      printf("bda:vm_start: env = %p jvmti = %p nthread = %x\n", env, jvmti, nthread);
  }
}

static void JNICALL bda_vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
  unsigned int nthread;

  nthread = GET_NATIVE_THREADID();

  if (bda_options.verbose >= 1) {
      printf("bda:vm_init: env = %p jvmti = %p nthread = %x\n", env, jvmti, nthread);
  }
}


static void JNICALL bda_vm_death(jvmtiEnv *jvmti, JNIEnv *env)
{
  bda_check_resource_leak(env);

  if (bda_options.mcount) {
    bda_j2c_proxy_dump_stat();
    bda_c2j_proxy_dump_stat();
  }

  if (bda_options.verbose >= 1) {
    printf("bda:vm_death: env = %p jvmti = %p\n", env, jvmti);
  }
  bda_jvmti = NULL;
}


static void JNICALL bda_vm_bind(jvmtiEnv *jvmti, JNIEnv *env,
                                jthread thread, jmethodID method,
                                void* address, void** new_address_ptr)
{
  jvmtiPhase phase;
  jvmtiError err;
  err = (*jvmti)->GetPhase(jvmti, &phase);
  assert(err == JVMTI_ERROR_NONE);
  if (!bda_is_agent_native_method(address)) {
    switch(phase) {
    case JVMTI_PHASE_ONLOAD:
    case JVMTI_PHASE_PRIMORDIAL:
        bda_j2c_proxy_add_deferred(method, address);
      if (bda_options.verbose >= 2) {
          printf("bda:native_bind: env = %p address = %p deferred\n",
                 env, address);
      }
      break;
    case JVMTI_PHASE_START:
    case JVMTI_PHASE_LIVE: {
        j2c_proxyid pid = bda_j2c_proxy_add(jvmti, env, method, address, new_address_ptr);
        int is_user_method = bda_j2c_proxy_is_user_method(pid);
        if (((bda_options.verbose >= 2) && is_user_method) || (bda_options.verbose >= 3)) {
            bda_jmethod_id jmid = bda_j2c_proxy_get_method_id(pid);
            const char * fname = bda_jmethod_ensure(jmid)->fullname;
            printf("bda:native_bind: env = %p address = %p active %s method %s\n",
                   env, address, (is_user_method ? "user": "system"), fname);
        }
        break;
    }
    case JVMTI_PHASE_DEAD:
      if (bda_options.verbose >= 2) {
          printf("bda:native_bind: env = %p address = %p deadphase\n",
                 env, address);
      }
      break;
    default:
      assert(0); /* not reachable. */
      break;
    }
  }
}

static void JNICALL bda_vm_data_dump_request(jvmtiEnv *jvmti)
{
  bda_j2c_proxy_dump_stat(jvmti);
  bda_c2j_proxy_dump_stat();
}

static void JNICALL bda_vm_thread_start(jvmtiEnv *jvmti, JNIEnv * env, jthread t)
{
  jvmtiError err;
  jvmtiPhase phase;
  jvmtiThreadInfo tinfo;
  unsigned int nthread;
  bda_state_id bid;
  struct bda_state_info * s;

  nthread = GET_NATIVE_THREADID();

  /* check phase*/
  err = (*jvmti)->GetPhase(jvmti, &phase);
  assert(err == JVMTI_ERROR_NONE);
  if (phase != JVMTI_PHASE_LIVE) {
    if (bda_options.verbose >= 1) {
      printf("bda:thread_start: env = %p thread = %p"
             " nthread = %x (not live phase)\n", env, t, nthread);
    }
    return;
  }

  bid = bda_state_allocate(env);
  s = bda_state_get(bid);
  s->nthreadid = nthread;
  err = (*jvmti)->SetThreadLocalStorage(jvmti, NULL, (void*)bid);
  assert(err == JVMTI_ERROR_NONE);

  /* thread name*/
  err = (*jvmti)->GetThreadInfo(jvmti, NULL, &tinfo);
  assert(err == JVMTI_ERROR_NONE);
  assert(tinfo.name != NULL);
  strncpy(s->name, tinfo.name, sizeof(s->name));
  if (sizeof(s->name) > 0) { s->name[sizeof(s->name) - 1] = '\0';}
  err = (*jvmti)->Deallocate(jvmti, (unsigned char*)tinfo.name);

  if (bda_options.verbose >= 1) {
      printf("bda:thread_start: env = %p thread = %p state_id = %d " 
             "nthread = %x name = %s\n", env, t, bid, s->nthreadid, s->name);
  }
}

static void JNICALL bda_vm_thread_end(jvmtiEnv *jvmti, JNIEnv * env, jthread t)
{
  jvmtiError err;
  bda_state_id bid;

  struct bda_state_info * s = bda_state_find(env);

  if (s != NULL) {
    err = (*jvmti)->GetThreadLocalStorage(jvmti, NULL, (void**)&bid);
    assert(err == JVMTI_ERROR_NONE);
    
    if (bda_options.verbose >= 1) {
      printf("bda:thread_end: env = %p thread = %p state_id = %d\n", env, t, bid);
    }
    
    bda_state_free(bid);
    err = (*jvmti)->SetThreadLocalStorage(jvmti, NULL, NULL);
    assert(err == JVMTI_ERROR_NONE);
  } else {
    /* A work around due to J9 JVMTI bug, which does not report the
     * begining. */
    if (bda_options.verbose >= 1) {
      printf("bda:thread_end: env = %p thread = %p JVMTI bug?\n", env, t);
    }
  }
}
