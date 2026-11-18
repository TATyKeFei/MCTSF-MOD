package icu.tatyrealms.tatykefei.state;

public interface EmptyBodyHolder {
    boolean tsf$isEmptyBody();

    void tsf$setEmptyBody(boolean emptyBody);

    boolean tsf$isFoldedSkin();

    void tsf$setFoldedSkin(boolean foldedSkin);

    String tsf$rigPose();

    void tsf$setRigPose(String pose);

    boolean tsf$showDisplayName();

    String tsf$displayName();

    boolean tsf$poseable();

    boolean tsf$showCape();

    String tsf$face();

    void tsf$setDisplayOptions(boolean showName, boolean poseable, boolean showCape, String face, String displayName);

    default boolean tsf$isMounted() {
        return tsf$isEmptyBody() && tsf$rigPose().startsWith("m,");
    }
}
