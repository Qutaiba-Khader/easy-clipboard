# Keep Serializable model classes intact for ObjectOutputStream persistence.
-keepclassmembers class com.easyclipboard.app.model.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Shizuku is optional; do not fail if absent.
-dontwarn rikka.shizuku.**
