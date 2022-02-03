package util.spark;

import mpicbg.models.RigidModel2D;

public class Serializers {

    public static double[] serializeRigidModel2D(RigidModel2D model) {
        double[] result = new double[6];
        model.toArray(result);
        return result;
    }

    public static RigidModel2D deserializeRigidModel2D(double[] data) {
        RigidModel2D result = new RigidModel2D();
        result.set(data[0], data[1], data[4], data[5]);
        return result;
    }
}
