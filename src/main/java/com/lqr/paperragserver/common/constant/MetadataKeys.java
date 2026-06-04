package com.lqr.paperragserver.common.constant;

/**
 * 文档 metadata 中使用的统一键名。
 */
public final class MetadataKeys {

    public static final String SOURCE_ID = "sourceId";
    public static final String OWNER_USER_ID = "ownerUserId";
    public static final String TITLE = "title";
    public static final String AUTHORS = "authors";
    public static final String ABSTRACT_TEXT = "abstractText";
    public static final String DOI = "doi";
    public static final String JOURNAL = "journal";
    public static final String PUBLISH_YEAR = "publishYear";
    public static final String KEYWORDS = "keywords";
    public static final String FILE_NAME = "fileName";
    public static final String CONTENT_TYPE = "contentType";
    public static final String CONTENT_LENGTH = "contentLength";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String SOURCE_TYPE_USER = "USER";
    public static final String SOURCE_TYPE_REVIEW = "REVIEW";

    public static final String EXTRACTION_MODE = "extractionMode";
    public static final String EXTRACTED_TEXT_LENGTH = "extractedTextLength";
    public static final String ASSET_COUNT = "assetCount";
    public static final String DOCUMENT_ASSETS = "documentAssets";
    public static final String RENDERED_PAGE_COUNT = "renderedPageCount";
    public static final String MULTIMODAL_TRUNCATED = "multimodalTruncated";

    public static final String ASSET_ID = "assetId";
    public static final String ASSET_IDS = "assetIds";
    public static final String ASSET_TYPE = "assetType";
    public static final String ASSET_CAPTION = "assetCaption";
    public static final String EMBEDDED_IMAGE_PATH = "embeddedImagePath";
    public static final String FILE_SIZE = "fileSize";
    public static final String CONTENT_HASH = "contentHash";
    public static final String TEXT_START = "textStart";
    public static final String TEXT_END = "textEnd";
    public static final String CAPTION_START = "captionStart";
    public static final String CAPTION_END = "captionEnd";

    public static final String CHUNK_ID = "chunkId";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String CHUNK_TYPE = "chunkType";
    public static final String CHUNK_START = "chunkStart";
    public static final String CHUNK_END = "chunkEnd";
    public static final String CHUNK_LENGTH = "chunkLength";
    public static final String CONTEXT_BEFORE_START = "contextBeforeStart";
    public static final String CONTEXT_BEFORE_END = "contextBeforeEnd";
    public static final String CONTEXT_AFTER_START = "contextAfterStart";
    public static final String CONTEXT_AFTER_END = "contextAfterEnd";

    public static final String PAGE_NUMBER = "pageNumber";
    public static final String SECTION_TITLE = "sectionTitle";
    public static final String SECTION_TYPE = "sectionType";
    public static final String SECTION_LEVEL = "sectionLevel";

    private MetadataKeys() {
    }
}