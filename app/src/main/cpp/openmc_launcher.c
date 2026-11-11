#include <jni.h>
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

typedef jint (*create_vm_fn)(JavaVM **, void **, void *);

static int checked_path(char *output, size_t capacity, const char *directory, const char *name) {
    int length = snprintf(output, capacity, "%s/%s", directory, name);
    return length > 0 && (size_t) length < capacity;
}

static void describe_file(const char *label, const char *path) {
    struct stat details;
    if (stat(path, &details) == 0) {
        fprintf(stderr, "OpenMC %s: %s (size=%lld)\n", label, path, (long long) details.st_size);
    } else {
        fprintf(stderr, "OpenMC %s: %s (missing, errno=%d)\n", label, path, errno);
    }
}

static int inferred_java_home(const char *jvm_path, char *output, size_t capacity) {
    int length = snprintf(output, capacity, "%s", jvm_path);
    if (length < 0 || (size_t) length >= capacity) return 0;
    for (int component = 0; component < 3; component++) {
        char *slash = strrchr(output, '/');
        if (!slash) return 0;
        *slash = '\0';
    }
    return 1;
}

static int self_test(JNIEnv *env) {
    jclass system = (*env)->FindClass(env, "java/lang/System");
    jmethodID property = system ? (*env)->GetStaticMethodID(
        env, system, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;") : NULL;
    jstring key = property ? (*env)->NewStringUTF(env, "java.version") : NULL;
    jstring version = key ? (jstring) (*env)->CallStaticObjectMethod(env, system, property, key) : NULL;
    if (!version || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 7;
    }
    const char *value = (*env)->GetStringUTFChars(env, version, NULL);
    if (!value) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 7;
    }
    printf("JVM_READY version=%s\n", value);
    fflush(stdout);
    (*env)->ReleaseStringUTFChars(env, version, value);
    return 0;
}

int main(int argc, char **argv) {
    const char *home = getenv("JAVA_HOME");
    const char *native_dir = getenv("OPENMC_NATIVE_DIR");
    const char *bootstrap = getenv("OPENMC_BOOTSTRAP_JAR");
    if (!home || !native_dir || !bootstrap || argc < 2) {
        fputs("Missing runtime paths or server JAR.\n", stderr);
        return 2;
    }

    char library[PATH_MAX];
    char linked_library[PATH_MAX];
    char modules[PATH_MAX];
    char inferred_modules[PATH_MAX];
    char home_option[PATH_MAX + 32];
    char classpath_option[PATH_MAX + 32];
    char heap_option[32];
    if (!checked_path(library, sizeof(library), native_dir, "libjvm.so") ||
        !checked_path(linked_library, sizeof(linked_library), home, "lib/server/libjvm.so") ||
        !checked_path(modules, sizeof(modules), home, "lib/modules") ||
        snprintf(home_option, sizeof(home_option), "-Djava.home=%s", home) >= (int) sizeof(home_option) ||
        snprintf(classpath_option, sizeof(classpath_option), "-Djava.class.path=%s", bootstrap) >= (int) sizeof(classpath_option)) {
        fputs("Runtime path is too long.\n", stderr);
        return 2;
    }
    const char *heap_env = getenv("OPENMC_HEAP_MB");
    long heap_mb = heap_env ? strtol(heap_env, NULL, 10) : 1024;
    if (heap_mb < 512 || heap_mb > 6144 || snprintf(heap_option, sizeof(heap_option), "-Xmx%ldm", heap_mb) >= (int) sizeof(heap_option)) {
        fputs("Invalid requested heap size.\n", stderr);
        return 2;
    }

    fprintf(stderr, "OpenMC JAVA_HOME: %s\nOpenMC native directory: %s\n", home, native_dir);
    describe_file("runtime modules", modules);
    describe_file("runtime JVM link", linked_library);
    describe_file("packaged JVM", library);
    describe_file("bootstrap", bootstrap);

    void *library_handle = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
    if (!library_handle) {
        fprintf(stderr, "Failed to load OpenJDK VM: %s\n", dlerror());
        return 3;
    }
    create_vm_fn create_vm = (create_vm_fn) dlsym(library_handle, "JNI_CreateJavaVM");
    if (!create_vm) {
        fputs("JNI_CreateJavaVM is absent.\n", stderr);
        dlclose(library_handle);
        return 3;
    }

    Dl_info loaded;
    if (dladdr((void *) create_vm, &loaded) && loaded.dli_fname) {
        fprintf(stderr, "OpenMC loaded JVM path: %s\n", loaded.dli_fname);
        char inferred_home[PATH_MAX];
        if (inferred_java_home(loaded.dli_fname, inferred_home, sizeof(inferred_home)) &&
            checked_path(inferred_modules, sizeof(inferred_modules), inferred_home, "lib/modules")) {
            fprintf(stderr, "OpenMC inferred JVM home: %s\n", inferred_home);
            describe_file("inferred boot modules", inferred_modules);
            if (strcmp(inferred_home, home) != 0) {
                fputs("OpenMC: JVM library location does not match JAVA_HOME; OpenJDK may reject its boot class path.\n", stderr);
            }
        }
    } else {
        fputs("OpenMC: dladdr could not report the loaded JVM path.\n", stderr);
    }

    JavaVMOption options[] = {
        { .optionString = home_option },
        { .optionString = classpath_option },
        { .optionString = "-Xms256m" },
        { .optionString = heap_option }
    };
    JavaVMInitArgs init = {
        .version = JNI_VERSION_1_6,
        .nOptions = (jint) (sizeof(options) / sizeof(options[0])),
        .options = options,
        .ignoreUnrecognized = JNI_FALSE
    };
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jint status = create_vm(&vm, (void **) &env, &init);
    if (status != JNI_OK || !vm || !env) {
        fprintf(stderr, "JNI_CreateJavaVM failed: %d\n", status);
        dlclose(library_handle);
        return 4;
    }

    if (strcmp(argv[1], "--self-test") == 0) {
        int result = self_test(env);
        (*vm)->DestroyJavaVM(vm);
        dlclose(library_handle);
        return result;
    }

    jclass entry = (*env)->FindClass(env, "org/openmc/server/bootstrap/ServerBootstrap");
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jmethodID main_method = entry ? (*env)->GetStaticMethodID(env, entry, "main", "([Ljava/lang/String;)V") : NULL;
    if (!entry || !string_class || !main_method || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        fputs("Bootstrap class unavailable.\n", stderr);
        (*vm)->DestroyJavaVM(vm);
        dlclose(library_handle);
        return 5;
    }

    jobjectArray arguments = (*env)->NewObjectArray(env, argc - 1, string_class, NULL);
    if (!arguments) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        (*vm)->DestroyJavaVM(vm);
        dlclose(library_handle);
        return 5;
    }
    for (int index = 1; index < argc; index++) {
        jstring value = (*env)->NewStringUTF(env, argv[index]);
        if (!value) break;
        (*env)->SetObjectArrayElement(env, arguments, index - 1, value);
        (*env)->DeleteLocalRef(env, value);
        if ((*env)->ExceptionCheck(env)) break;
    }
    if (!(*env)->ExceptionCheck(env)) {
        (*env)->CallStaticVoidMethod(env, entry, main_method, arguments);
    }
    int result = 0;
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        result = 6;
    }
    (*vm)->DestroyJavaVM(vm);
    dlclose(library_handle);
    return result;
}
