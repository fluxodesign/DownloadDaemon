package net.fluxo.dd;

import net.fluxo.dd.dbo.ADTObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * @author Ronald Kurniawan
 * @version 0.5, 18/08/15.
 */
@Provider
@Consumes("application/json")
public class AriaUpdateProvider implements MessageBodyReader<ADTObject> {
	@Override
	public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
		return ADTObject.class == type;
	}

	@Override
	public ADTObject readFrom(Class<ADTObject> aClass, Type type, Annotation[] annotations, MediaType mediaType,
		MultivaluedMap<String, String> multivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
		ObjectInputStream ois = new ObjectInputStream(inputStream);
		ADTObject object = null;
		try {
			object = (ADTObject) ois.readObject();
		} catch (ClassNotFoundException cnfe) {
			cnfe.printStackTrace();
		}
		return object;
	}
}
