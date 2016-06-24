package retrofit2.converter.protobuf.nano;

import java.io.IOException;
import java.util.List;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static retrofit2.converter.protobuf.nano.PhoneProtos.Phone;

public final class ProtoConverterTest {
  interface Service {
    @GET("/")
    Call<Phone> get();
    @POST("/") Call<Phone> post(@Body Phone impl);
    @GET("/") Call<String> wrongClass();
    @GET("/") Call<List<String>> wrongType();
  }

  @Rule
  public final MockWebServer server = new MockWebServer();

  private Service service;

  @Before public void setUp() {
    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ProtoConverterFactory.create())
            .build();
    service = retrofit.create(Service.class);
  }

  @Test
  public void serializeAndDeserialize() throws IOException, InterruptedException {
    ByteString encoded = ByteString.decodeBase64("Cg4oNTE5KSA4NjctNTMwOQ==");
    server.enqueue(new MockResponse().setBody(new Buffer().write(encoded)));

    Phone phone = new Phone();
    phone.number = "(519) 867-5309";
    Call<Phone> call = service.post(phone);
    Response<Phone> response = call.execute();
    Phone body = response.body();
    assertThat(body.number).isEqualTo("(519) 867-5309");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getBody().readByteString()).isEqualTo(encoded);
    assertThat(request.getHeader("Content-Type")).isEqualTo("application/x-protobuf");
  }

  @Test public void deserializeEmpty() throws IOException {
    server.enqueue(new MockResponse());

    Call<Phone> call = service.get();
    Response<Phone> response = call.execute();
    Phone body = response.body();
    assertThat(body.number).isEqualTo("");
    assertThat(body.voicemail).isFalse();
  }

  @Test public void deserializeWrongClass() throws IOException {
    ByteString encoded = ByteString.decodeBase64("Cg4oNTE5KSA4NjctNTMwOQ==");
    server.enqueue(new MockResponse().setBody(new Buffer().write(encoded)));

    try {
      service.wrongClass();
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage(""
              + "Unable to create converter for class java.lang.String\n"
              + "    for method Service.wrongClass");
      assertThat(e.getCause()).hasMessage(""
              + "Could not locate ResponseBody converter for class java.lang.String.\n"
              + "  Tried:\n"
              + "   * retrofit2.BuiltInConverters\n"
              + "   * retrofit2.converter.protobuf.nano.ProtoConverterFactory");
    }
  }

  @Test public void deserializeWrongType() throws IOException {
    ByteString encoded = ByteString.decodeBase64("Cg4oNTE5KSA4NjctNTMwOQ==");
    server.enqueue(new MockResponse().setBody(new Buffer().write(encoded)));

    try {
      service.wrongType();
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage(""
              + "Unable to create converter for java.util.List<java.lang.String>\n"
              + "    for method Service.wrongType");
      assertThat(e.getCause()).hasMessage(""
              + "Could not locate ResponseBody converter for java.util.List<java.lang.String>.\n"
              + "  Tried:\n"
              + "   * retrofit2.BuiltInConverters\n"
              + "   * retrofit2.converter.protobuf.nano.ProtoConverterFactory");
    }
  }

  @Test public void deserializeWrongValue() throws IOException {
    ByteString encoded = ByteString.decodeBase64("////");
    server.enqueue(new MockResponse().setBody(new Buffer().write(encoded)));

    Call<?> call = service.get();
    try {
      call.execute();
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getCause()).isInstanceOf(InvalidProtocolBufferNanoException.class)
              .hasMessageContaining("input ended unexpectedly");
    }
  }
}
