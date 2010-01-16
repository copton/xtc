#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "options.h"

struct bda_options bda_options =
{
    0, /* mcount */
    0, /* verbose */
    0, /* jnicheck */
    0, /* dbgwait */
    0, /* nointerpose */
    {'\0'}, /* method name for tracing */
    {'*', '\0'}, /* thread name for tracing */
};

struct bda_option_key_value {
    const char * name;
    int name_len;
    const char * value;
    int value_len;
};

static void bda_usage(const char * reason)
{
    if (reason) {
        printf("can not understand: %s\n", reason);
    }
    printf(
        "    BDA: Blink Debug Agent\n"
        "usage: java -agentlib:bda=[help]|[<option>=<value>, ...]\n"
        "Options\n"
        "verbose=0,1,..    0  Print verbose messages at the specificed level.\n"
        "mcount=y|n        n  Report the execution counts for Java native methods and JNI functions.\n"
        "jnicheck=y|n      n  Report any illegal use of JNI Functions.\n"
        "dbgwait=y|n       n  Wait for the debugger to attach during the JVM initialization.\n"
        "nointerpose=y|n   n  Do not interpose transitions for experimental measurement.\n"
        "mname=[a-zA-Z*]+  *  Method name for tracing transitions.\n"
        "tname=[a-zA-Z*]+  *  Thread name for tracing transitions.\n"
        );
    exit(0);
}

static int bda_strcmp(const char * s1, int s1_len, const char* s2)
{
    return (s1_len == strlen(s2)) && (strncmp(s1, s2, s1_len) == 0);
}

static int bda_handle_boolean_option(const char *opt_name, int *value,
                                     struct bda_option_key_value * opt,
                                     const char *usage)
{
    if ( (opt->name_len == strlen(opt_name)) && bda_strcmp(opt->name, opt->name_len, opt_name))  {
        if (bda_strcmp(opt->value, opt->value_len, "y")) {
            *value = 1;
        } else if (bda_strcmp(opt->value, opt->value_len, "n")) {
            *value = 0;
        } else {
            bda_usage(usage);
        }
        return 1;
    } else {
        return 0;
    }
}

static void bda_handle_option(struct bda_option_key_value * opt)
{
    if (bda_strcmp(opt->name, opt->name_len, "verbose")) {
        char t[5];
        int i;
        if (opt->value_len > 4) {
            bda_usage("too high verbose level");
        }
        for(i=0;i< opt->value_len;i++) {
            if ( opt->value[i] < '0' || opt->value[i] > '9') {
                bda_usage("please specify numbers for verbose option");
            }
        }
        strncpy(t, opt->value, opt->value_len);
        t[4] = '\0';
        bda_options.verbose = atoi(t);
    } else if (bda_strcmp(opt->name, opt->name_len, "mname")) {
        int i;
        if ((opt->name_len+1) < sizeof(bda_options.mname)) {
            for(i=0;i < opt->value_len;i++) {
                bda_options.mname[i] = opt->value[i];
            }
            bda_options.mname[i+1] = '\0';
        } else {
            bda_usage("method name pattern is too long");
        }
    } else if (bda_strcmp(opt->name, opt->name_len, "tname")) {
        int i;
        if ((opt->name_len+1) < sizeof(bda_options.tname)) {
            for(i=0;i < opt->value_len;i++) {
                bda_options.tname[i] = opt->value[i];
            }
            bda_options.tname[i+1] = '\0';
        } else {
            bda_usage("thread name pattern is too long");
        }
    } else if (bda_handle_boolean_option("mcount", &bda_options.mcount, 
                                         opt, "mcount takes either y or n")
               || bda_handle_boolean_option("mcount", &bda_options.mcount, 
                                            opt, "mcount takes either y or n")
               || bda_handle_boolean_option("jnicheck", &bda_options.jnicheck, 
                                            opt, "jnicheck takes either y or n")
               || bda_handle_boolean_option("dbgwait", &bda_options.dbgwait, 
                                            opt, "dbgwait takes either y or n")
               || bda_handle_boolean_option("nointerpose", &bda_options.nointerpose, 
                                            opt, "nointerpose takes either y or n")) {
    } else {
        bda_usage("can not recognize the option");
    }
}

void bda_parse_options(const char * options)
{
  /* parse options. */
  if (options != NULL) {
      int s = 0, i;
      int name_index = 0, name_length = 0;
      int value_index, value_length;
      for(i = 0; options[i] != '\0';i++) {
          char c = options[i];
          if (!s) {
              if ( c != '=') {
                  name_length++;
              } else {
                  value_index = i + 1;
                  value_length = 0;
                  s = 1;
              }
          } else {
              if (c != ',') {
                  value_length++;
              } else {
                  struct bda_option_key_value opt = {
                      options + name_index, name_length, 
                      options + value_index, value_length};
                  bda_handle_option(&opt);
                  name_index = i+1;
                  name_length = 0;
                  s = 0;
              }
          }
      }
      if (!s) {
          if (bda_strcmp(options + name_index, name_length, "help")) {
              bda_usage(NULL);
          } else if (name_length > 0) {
              bda_usage(options);
          }
      } else {
          struct bda_option_key_value opt = {
              options + name_index, name_length, 
              options + value_index, value_length};
          bda_handle_option(&opt);
      }
  }
}

static int bda_pmatch(const char * p, const char * s)
{
    if (*p == '\0') {
        return 1;
    } else {
        if (*p == '*') {
            while(*s) {
                if (bda_pmatch(p+1,s)) {
                    return 1;
                } else {
                    s++;
                }
            }
            return 0;
        } else {
            if (*p == *s) {
                return bda_pmatch(p+1, s+1);
            } else {
                return 0;
            }
        }
    }
}

int bda_trace_match(const char * mname, const char * tname)
{
  assert(bda_options.mname[0] != '\0');
  if (bda_pmatch(bda_options.mname, mname)) {
    if (bda_options.tname[0] == '*' && bda_options.tname[1] == '\0') {
        return 1;
    } else {
        return bda_pmatch(bda_options.tname, tname);
    }
  } else {
      return 0;
  }
}
