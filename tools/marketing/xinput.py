"""Tiny XTEST driver for the screenshot client: move, click, press keys and type on an X display.

Usage: python3 xinput.py move X Y | click X Y [button] | shiftclick X Y | key NAME | hold NAME SECONDS | type TEXT |
       scroll X Y N | cursor THEME SIZE
Needs python-xlib (pip install python-xlib). DISPLAY selects the Xvfb display.
"""
import sys
import time

from Xlib import X, XK, display

from Xlib.ext import xtest

d = display.Display()


def move(x, y):
    xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y))
    d.sync()


def press(button):
    xtest.fake_input(d, X.ButtonPress, int(button))
    d.sync()
    time.sleep(0.06)
    xtest.fake_input(d, X.ButtonRelease, int(button))
    d.sync()


def click(x, y, button=1):
    move(x, y)
    time.sleep(0.08)
    press(button)


def shift_click(x, y):
    shift = d.keysym_to_keycode(XK.string_to_keysym("Shift_L"))
    move(x, y)
    time.sleep(0.08)
    xtest.fake_input(d, X.KeyPress, shift)
    d.sync()
    time.sleep(0.05)
    press(1)
    time.sleep(0.05)
    xtest.fake_input(d, X.KeyRelease, shift)
    d.sync()


def scroll(x, y, amount):
    move(x, y)
    button = 4 if int(amount) > 0 else 5
    for _ in range(abs(int(amount))):
        press(button)
        time.sleep(0.05)


def keycode(name):
    keysym = XK.string_to_keysym(name)
    if keysym == 0 and len(name) == 1:
        keysym = ord(name)
    return d.keysym_to_keycode(keysym)


def key(name, shift=False):
    code = keycode(name)
    shift_code = d.keysym_to_keycode(XK.string_to_keysym("Shift_L"))
    if shift:
        xtest.fake_input(d, X.KeyPress, shift_code)
    xtest.fake_input(d, X.KeyPress, code)
    d.sync()
    time.sleep(0.04)
    xtest.fake_input(d, X.KeyRelease, code)
    if shift:
        xtest.fake_input(d, X.KeyRelease, shift_code)
    d.sync()
    time.sleep(0.03)


SPECIAL = {" ": ("space", False), "/": ("slash", False), ".": ("period", False), "-": ("minus", False),
           "_": ("minus", True), ":": ("semicolon", True), "!": ("1", True), "@": ("2", True), "?": ("slash", True),
           ",": ("comma", False), "'": ("apostrophe", False), "[": ("bracketleft", False), "]": ("bracketright", False),
           "|": ("backslash", True), "$": ("4", True), "#": ("3", True), "&": ("7", True), "(": ("9", True),
           ")": ("0", True), "=": ("equal", False), "+": ("equal", True), '"': ("apostrophe", True),
           ";": ("semicolon", False), "<": ("comma", True), ">": ("period", True)}


def hold(name, seconds):
    """Holds a key down (e.g. Tab for the player list) for a while."""
    code = keycode(name)
    xtest.fake_input(d, X.KeyPress, code)
    d.sync()
    time.sleep(float(seconds))
    xtest.fake_input(d, X.KeyRelease, code)
    d.sync()


def cursor(theme, size):
    """Gives the root window a themed arrow (e.g. Adwaita 64), so captures show a big, readable pointer instead of
    the bare X server's small cross. Needs libXcursor."""
    import ctypes
    import ctypes.util
    x11 = ctypes.cdll.LoadLibrary(ctypes.util.find_library("X11"))
    xc = ctypes.cdll.LoadLibrary(ctypes.util.find_library("Xcursor"))
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XDefaultRootWindow.restype = ctypes.c_ulong
    x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    x11.XDefineCursor.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_ulong]
    x11.XFlush.argtypes = x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    x11.XSetCloseDownMode.argtypes = [ctypes.c_void_p, ctypes.c_int]
    xc.XcursorSetTheme.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    xc.XcursorSetDefaultSize.argtypes = [ctypes.c_void_p, ctypes.c_int]
    xc.XcursorLibraryLoadCursor.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    xc.XcursorLibraryLoadCursor.restype = ctypes.c_ulong
    dpy = x11.XOpenDisplay(None)
    xc.XcursorSetTheme(dpy, theme.encode())
    xc.XcursorSetDefaultSize(dpy, int(size))
    shape = xc.XcursorLibraryLoadCursor(dpy, b"default")
    if not shape:
        sys.exit(f"no cursor 'default' in theme {theme}")
    x11.XDefineCursor(dpy, x11.XDefaultRootWindow(dpy), shape)
    # Keep the cursor after this client leaves (capture tools cannot read a cursor whose owner is gone).
    x11.XSetCloseDownMode(dpy, 1)
    x11.XFlush(dpy)
    x11.XCloseDisplay(dpy)


def type_text(text):
    for char in text:
        if char in SPECIAL:
            name, shift = SPECIAL[char]
            key(name, shift=shift)
        elif char.isupper():
            key(char.lower(), shift=True)
        else:
            key(char)


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "move":
        move(sys.argv[2], sys.argv[3])
    elif command == "click":
        click(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else 1)
    elif command == "shiftclick":
        shift_click(sys.argv[2], sys.argv[3])
    elif command == "scroll":
        scroll(sys.argv[2], sys.argv[3], sys.argv[4])
    elif command == "key":
        key(sys.argv[2])
    elif command == "hold":
        hold(sys.argv[2], sys.argv[3])
    elif command == "cursor":
        cursor(sys.argv[2], sys.argv[3])
    elif command == "type":
        type_text(" ".join(sys.argv[2:]))
