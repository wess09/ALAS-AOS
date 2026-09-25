/* Dynamically linked aarch64 guest for Spike A.

   Mirrors the product scenario: an ELF payload copied into the app data dir that
   under `proot -r` needs its interpreter (PT_INTERP=/system/bin/linker64) and
   libc resolved.

   With `--exec <path> [args...]` it execve()s the child instead of printing,
   which probes whether a *guest-initiated* execve inside the proot rootfs works
   (the AzurPilot-spawns-python-subprocess case).
*/
#include <stdio.h>
#include <string.h>
#include <sys/utsname.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc >= 3 && strcmp(argv[1], "--exec") == 0) {
        execv(argv[2], &argv[2]);
        perror("nested execv");
        return 126;
    }
    struct utsname u;
    uname(&u);
    printf("DYNAMIC_GUEST_OK pid=%d machine=%s release=%s\n", (int)getpid(), u.machine, u.release);
    fflush(stdout);
    return 0;
}
