package icu.tatyrealms.tatykefei.state;

public interface EmptyBodyHolder {
    boolean tsf$isEmptyBody();

    void tsf$setEmptyBody(boolean emptyBody);

    boolean tsf$isFoldedSkin();

    void tsf$setFoldedSkin(boolean foldedSkin);

    String tsf$rigPose();

    void tsf$setRigPose(String pose);

    default boolean tsf$isMounted() {
        return tsf$isEmptyBody() && tsf$rigPose().startsWith("m,");
    }
}
