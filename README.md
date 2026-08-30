[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# Gallery 

### A simple Android image and video viewer.


### ADB

#### Install/Uninstall Apps
```
adb uninstall com.robertotorino.gallery
adb install app\build\outputs\apk\debug\gallery-debug.apk
adb shell pm clear com.robertotorino.gallery
```

### GitHub

```powershell
git pull origin master

git remote add origin git@github.com:RobertoTorino/Gallery.git
git push -u origin master
git fetch --all
git remote -v
```


![qrcode-gh.png](media/qrcode-gh.png)


**[RobertoTorino](https://github.com/RobertoTorino)**        

Fixed issue where deleting pictures was not persistent caused by the app failing to remove images from the systems MediaStore on Android 10plus devices. In modern Android versions apps require explicit user permission to delete or modify media files they do not own. The original code attempted to delete these files silently in the background which failed due to missing permissions causing the images to reappear when the app rescanned the MediaStore upon restart.
When moving to the Recycle Bin or Archive the app now first copies the file to its private storage.
It then uses MediaStore.createDeleteRequest to show a system dialog asking you for permission to delete the original file.
Only after you confirm the deletion in the system dialog does the app update its internal database and remove the image from the gallery view. If you cancel the dialog, the app automatically cleans up the temporary copy in the bin.
Changed deleteImages to deleteMedia and archiveImages to archiveMedia to reflect that they handle any media type pictures or videos.
The biometric prompt now dynamically displays Authenticate to delete pictures or Authenticate to delete videos based on the items being deleted.
The ArchiveSelectionDialog now uses dynamic labels instead of a generic Selection label.
Verified that video deletion from the VideoPlayerDialog correctly triggers the same persistent deletion Recycle Bin flow used for images.
Fixed several compiler warnings and unresolved references introduced during the previous refactoring.
The biometric check still occurs immediately after you confirm the deletion in the app's internal dialog and only upon successful authentication does the app proceed to request the system level MediaStore permission.
