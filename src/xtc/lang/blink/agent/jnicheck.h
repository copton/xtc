#ifndef _JNI_CHECK_H_
#define _JNI_CHECK_H_

#include <jni.h>
#include "state.h"

#define MAX_GLOBAL_REFERENCES (4096)

enum bda_var_arg_type {BDA_VA_LIST, BDA_JARRAY};

struct bda_var_arg_wrap {
    enum bda_var_arg_type type;
    union {
        va_list ap;
        const jvalue* array;
    } value;
};

extern void bda_jnicheck_init(JNIEnv* env);

/* local references. */
extern void bda_local_ref_init(struct bda_state_info * s);
extern void bda_local_ref_enter(struct bda_state_info * s, int capacity, int sentinel);
extern void bda_local_ref_leave(struct bda_state_info * s);
extern void bda_local_ref_add(struct bda_state_info *s, jobject o);
extern void bda_local_ref_delete(struct bda_state_info *s, jobject o);

/* local reference capacity. */
extern int bda_check_capacity_c2j_call(struct bda_state_info * s, const char *fname);
extern int bda_check_capacity_j2c_return(struct bda_state_info * s);

/* global references. */
extern void bda_global_ref_add(jobject o, int weak);
extern void bda_global_ref_delete(jobject o, int weak);

/* Basic JNI checks. */
extern int bda_check_env_match(struct bda_state_info * s, JNIEnv *env, const char * fname);
extern int bda_check_no_exeception(struct bda_state_info * s, const char * fname);
extern int bda_check_no_critical(struct bda_state_info * s, const char *fname);

/* NULL check for both JNI and C types. */
extern int bda_check_non_null(struct bda_state_info * s, const void * o, int index, const char * fname);

/* JNI reference checks. */

extern jclass bda_clazz_string;
extern jclass bda_clazz_class;
extern jclass bda_clazz_classloader;
extern jclass bda_clazz_throwable;
extern jclass bda_clazz_booleanArray;
extern jclass bda_clazz_byteArray;
extern jclass bda_clazz_charArray;
extern jclass bda_clazz_shortArray;
extern jclass bda_clazz_intArray;
extern jclass bda_clazz_longArray;
extern jclass bda_clazz_floatArray;
extern jclass bda_clazz_doubleArray;
extern jclass bda_clazz_field;
extern jclass bda_clazz_method;
extern jclass bda_clazz_constructor;
extern jclass bda_clazz_nio_buffer;

extern int bda_check_dref(struct bda_state_info * s,  jobject obj, int index, const char * fname);
extern int bda_check_jclass(struct bda_state_info * s, jclass clazz, int index, const char * fname);
extern int bda_check_jstring(struct bda_state_info * s, jstring jstr, int index, const char * fname);
extern int bda_check_jthrowable(struct bda_state_info * s, jthrowable t, int index, const char * fname);
extern int bda_check_jweak(struct bda_state_info *s, jweak ref, int index, const char * fname);
extern int bda_check_jarray(struct bda_state_info *s, jarray ref, int index, const char * fname);
extern int bda_check_jbooleanArray(struct bda_state_info *s, jbooleanArray ref, int index, const char * fname);
extern int bda_check_jbyteArray(struct bda_state_info *s, jbyteArray ref, int index, const char * fname);
extern int bda_check_jcharArray(struct bda_state_info *s, jcharArray ref, int index, const char * fname);
extern int bda_check_jshortArray(struct bda_state_info *s, jshortArray ref, int index, const char * fname);
extern int bda_check_jintArray(struct bda_state_info *s, jintArray ref, int index, const char * fname);
extern int bda_check_jlongArray(struct bda_state_info *s, jlongArray ref, int index, const char * fname);
extern int bda_check_jfloatArray(struct bda_state_info *s, jfloatArray ref, int index, const char * fname);
extern int bda_check_jdoubleArray(struct bda_state_info *s, jdoubleArray ref, int index, const char * fname);
extern int bda_check_jobjectArray(struct bda_state_info *s, jobjectArray ref, int index, const char * fname);

extern int bda_check_jobject_reflected_method(struct bda_state_info *s, jobject obj, int index, const char * fname);
extern int bda_check_jclass_scalar_allocatable(struct bda_state_info *s, jclass clazz, int index, const char * fname);
extern int bda_check_jarray_primitive(struct bda_state_info *s, jarray array, int index, const char * fname);
extern int bda_check_jobject_ref_type(struct bda_state_info *s, jobject obj, jobjectRefType ref_type, int index, const char * fname);

extern int bda_check_instance_jobject_jclass(struct bda_state_info *s, jobject obj, jclass clazz, int index, const char * fname);
extern int bda_check_subclass_jobject_jclass(struct bda_state_info *s, jobject obj, jclass clazz, int index, const char * fname);
extern int bda_check_subclass_jclass_jclass(struct bda_state_info *s, jclass sub_clazz, jclass sup_clazz, int index, const char * fname);
extern int bda_check_assignable_jclass_jobject(struct bda_state_info *s, jclass clazz, jobject obj, int index, const char * fname);
extern int bda_check_assignable_jobjectArray_jobject(struct bda_state_info *s, jobjectArray array, jobject obj, int index, const char * fname);

/* jfieldID checks. */
extern void bda_jfieldid_append(struct bda_state_info * s, jfieldID fid, jclass clazz, short is_static, const char *name, const char * desc);
extern int bda_check_jfieldid_to_reflected_field(struct bda_state_info * s, jclass c, jfieldID f, jboolean is_static);
extern int bda_check_jfieldid_get_instance(struct bda_state_info * s, jobject o, jfieldID f, char vt, const char * fname);
extern int bda_check_jfieldid_set_instance(struct bda_state_info * s, jobject o, jfieldID f, const jvalue v, char vt, const char * fname);
extern int bda_check_jfieldid_get_static(struct bda_state_info * s, jclass c, jfieldID f,  char vt, const char * fname);
extern int bda_check_jfieldid_set_static(struct bda_state_info * s, jclass c, jfieldID f, const jvalue v, char vt, const char * fname);

/* jmethodID checks. */
extern void bda_jmethodid_append(jmethodID mid, short is_static, jclass clazz, const char * name, const char * desc);
extern int bda_check_jmethodid_to_reflected(struct bda_state_info * s, jclass c, jmethodID m, jboolean b, const char *fname);
extern int bda_check_jmethodid_new_object(struct bda_state_info * s, jclass c, jmethodID m, struct bda_var_arg_wrap a, const char *fname);
extern int bda_check_jmethodid_instance(struct bda_state_info * s, jobject o, jmethodID m, struct bda_var_arg_wrap a, const char *fname, char rt);
extern int bda_check_jmethodid_nonvirtual(struct bda_state_info * s, jobject o, jclass c, jmethodID m, struct bda_var_arg_wrap a, const char *fname, char rt);
extern int bda_check_jmethodid_static(struct bda_state_info * s, jclass c, jmethodID m, struct bda_var_arg_wrap a, const char *fname, char rt);

/* access. */
extern int bda_check_access_set_instance_field(struct bda_state_info * s, jobject c, jfieldID fid, int index, const char *fname);
extern int bda_check_access_set_static_field(struct bda_state_info * s, jclass c, jfieldID fid, int index, const char *fname);

/* resource. */
extern void bda_resource_acquire(struct bda_state_info * s, const void * resource, const char *fname);
extern void bda_resource_release(struct bda_state_info * s, const void * resource, const char *fname);

extern int bda_check_resource_free(struct bda_state_info * s, const void * resource, const char *fname);
extern int bda_check_resource_leak(JNIEnv *env);

/* critical. */
extern void bda_enter_critical(struct bda_state_info * s);
extern void bda_leave_critical(struct bda_state_info * s);

#endif /* _JNI_CHECK_H_ */
