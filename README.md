android-musicplayer
===================

A music playback and control library for Android applications. This library is an encapsulation 
of the [android-UniversalMusicPlayer][1].

Install
--------

1. Copy the musicplayer-VERSION.aar into the libs directory of your app module.
2. Add the following code into the build.gradle (Module: app):
```groovy
compile fileTree(include: ['*.jar'], dir: 'libs')
compile(name: 'musicplayer-VERSION', ext: 'aar')
```
3. Add the following code into the AndroidManifest.xml of the module above:
```xml
<service android:name="com.ic2lab.api.musicplayer.MusicService" />
```
4. If your app needs access to music files on the local storage, remember to add 
READ_EXTERNAL_STORAGE permission into AndroidManifest.xml, and check this permission 
when app running.
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```


Usage
--------

1. Config via MusicConfig in the onCreate() of your Application. Make sure you have called 
MusicConfig.get().setMetadataTransformer(). The android-musicplayer will use it to build 
MediaMetadaCompat.
2. Use MusicConnectionManager to connect your Activity with MusicService when onStart(), and 
disconnect when onDestroy().
3. MusicPlayer provides music playback control methods. You can call them after your Activity 
has connected with the MusicService, or let MusicConnectionManager help you finding a connected 
Activity or its controller by findXxx() methods of MusicConnectionManager.
4. We use QueueItem to pass songs to android-musicplayer and MediaMetadataCompat to give you 
infomation about the song playing. So you need to convert your own song type with QueueItem and 
MediaMetadataCompat to use android-musicplayer.   
For convenience, you can convert them by defining a class extending TypeQueueHelper.


Sample
--------

See the app module in the project root directory.


[1]: https://github.com/googlesamples/android-UniversalMusicPlayer