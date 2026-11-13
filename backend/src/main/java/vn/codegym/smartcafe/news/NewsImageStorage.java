package vn.codegym.smartcafe.news;

interface NewsImageStorage {
    StoredImage upload(byte[] bytes, String mimeType);
    byte[] read(String assetId);
    void delete(String assetId);

    record StoredImage(String assetId, String secureUrl) {}
}
