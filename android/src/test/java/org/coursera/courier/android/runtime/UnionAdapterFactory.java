package org.coursera.courier.android.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * GSON {@link com.google.gson.TypeAdapterFactory} for Pegasus style unions.
 * @param <T>
 */
public class UnionAdapterFactory<T> implements TypeAdapterFactory {

  private Class<T> _unionClass;

  public UnionAdapterFactory(Class<T> unionClass) {
    _unionClass = unionClass;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (type.getType().equals(_unionClass)) {
      return new UnionAdapter<T>(gson);
    } else {
      throw new IllegalArgumentException(
          "ExampleUnion.JsonAdapter may only be used with " + _unionClass.getName() + ", but was used " +
              "with: " + type.getRawType().getClass().getName());
    }
  }

  private static class UnionAdapter<T> extends TypeAdapter<T> {
    private Gson _gson;
    public UnionAdapter(Gson gson) {
      _gson = gson;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
      // Since union types are abstract, they can never be directly written, and the
      // member types are already deserializable.
      throw new UnsupportedOperationException();
    }

    @Override
    public T read(JsonReader reader) throws IOException {
      // Ideally we would stream the data from the reader here, but we need to inspect the
      // 'typeName' field, which may appear after the 'definition' field.
      reader.beginObject();
      String memberKey = reader.nextName();
      Object result = _gson.getAdapter(Class.forName(memberKey)).fromJson(reader);
      reader.endObject();
      return (T)result;
    }
  }
}
