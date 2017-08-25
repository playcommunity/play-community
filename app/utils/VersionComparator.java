package utils;

/**
 * Compare two version numbers version1 and version2.
 * If version1 > version2 return 1, if version1 < version2 return -1, otherwise return 0.
 */
public class VersionComparator {
    public static int compareVersion(String version1, String version2) {
        // 去除alpha或beta版本，例如1.9.1-beta1
        version1 = version1.split("-")[0];
        version2 = version2.split("-")[0];
        String[] ver1s = version1.split("\\.");
        String[] ver2s = version2.split("\\.");
        int len = Math.min(ver1s.length, ver2s.length);
        for (int i=0; i<len; i++){
            int v1 = Integer.valueOf(ver1s[i]);
            int v2 = Integer.valueOf(ver2s[i]);
            if (v1 < v2) {
                return -1;
            } else if (v1 > v2){
                return 1;
            }
        }
        if (ver1s.length > ver2s.length) {
            for (int i = ver2s.length; i < ver1s.length; i++){
                if (Integer.valueOf(ver1s[i]) > 0) {
                    return 1;
                }
            }
        } else if (ver1s.length < ver2s.length){
            for (int i = ver1s.length; i < ver2s.length; i++){
                if (Integer.valueOf(ver2s[i]) > 0) {
                    return -1;
                }
            }
        }
        return 0;
    }
}
