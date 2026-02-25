// Conditional import: selects the web implementation when running in a browser,
// and the native (FFI) implementation on Android / desktop.
export 'connection_native.dart'
    if (dart.library.html) 'connection_web.dart';
