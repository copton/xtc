#ifndef _OPTIONS_H_
#define _OPTIONS_H_

struct bda_options {
    int mcount;   /* if nonzero, report counts */
    int verbose;  /* verbose level. */
    int jnicheck; /* enable checking calls into JNI functions. */
    int dbgwait;  /* wait for the debugger attach. */
    int nointerpose; /* do not interpose the transition between Java and C. */
    char mname[1024];  /* method name for tracing. */
    char tname[1024];  /* thread name for tracing. */
};

extern struct bda_options bda_options;

extern void bda_parse_options(const char * options);

extern int bda_trace_match(const char * mname, const char * tname);

#endif /* _OPTIONS_H_ */
