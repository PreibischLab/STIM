package tools;

import java.lang.reflect.Type;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import net.imglib2.realtransform.AffineTransform3D;

public class AffineTransform3DAdapter implements JsonDeserializer<AffineTransform3D>, JsonSerializer<AffineTransform3D> {

	@Override
	public AffineTransform3D deserialize(
			final JsonElement json,
			final Type typeOfT,
			final JsonDeserializationContext context) throws JsonParseException {

		final JsonArray array = json.getAsJsonArray();
		final double[] values = new double[array.size()];
		for (int i = 0; i < values.length; ++i)
			values[i] = array.get(i).getAsDouble();
		final AffineTransform3D affine = new AffineTransform3D();
		affine.set(values);
		return affine;
	}

	@Override
	public final JsonElement serialize(
			final AffineTransform3D affine,
			final Type typeOfSrc,
			final JsonSerializationContext context) {

		return context.serialize(affine.getRowPackedCopy());
	}
}
