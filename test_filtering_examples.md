# Text Filtering Examples

## Before vs After Improvements

### Example 1: Japanese Text with English Voice
**Input**: "フォロー中の萌球風船さんが新しを公開しました！"

**Before (old replace mode)**:
```
[unreadable] [unreadable] [unreadable] [unreadable] [unreadable] [unreadable] [unreadable] [unreadable] [unreadable] [unreadable]
```

**After (new replace mode)**:
```
[unreadable]
```

### Example 2: Mixed Language Text
**Input**: "Meeting at 3PM 会議は3PM開始です"

**Skip mode**:
```
Meeting at 3PM
```

**Replace mode**:
```
Meeting at 3PM [unreadable]
```

**Attempt mode**:
```
Meeting at 3PM 会議は3PM開始です
```

### Example 3: Korean Text with English Voice
**Input**: "안녕하세요 Hello world 안녕"

**Replace mode**:
```
Hello world [unreadable]
```

### Example 4: Arabic Text with English Voice
**Input**: "مرحبا Hello السلام عليكم"

**Replace mode**:
```
Hello [unreadable]
```

## How It Works

The new filtering algorithm:
1. **Splits text into words**
2. **Checks each word** against the voice's supported character pattern
3. **Groups consecutive unsupported words** into a single `[unreadable]` marker
4. **Preserves supported words** in their original positions

This creates much cleaner output while still indicating where unsupported text was found.
