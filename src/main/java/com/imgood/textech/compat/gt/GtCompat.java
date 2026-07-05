package com.imgood.textech.compat.gt;

import java.lang.reflect.Field;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * GT5u version-compatible reflection facade for IGregTechTileEntity.
 * All methods return safe defaults when reflection fails (no GT loaded, field renamed, etc.).
 */
public final class GtCompat {

    private GtCompat() {}

    public static boolean isActive(IGregTechTileEntity te) {
        try {
            return te.isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getErrorDisplayID(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                return (Integer) metaTile.getClass()
                    .getMethod("getErrorDisplayID")
                    .invoke(metaTile);
            }
        } catch (Throwable t) {
            // fall through
        }
        return 0;
    }

    public static int getProblemDisplayID(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                return (Integer) metaTile.getClass()
                    .getMethod("getProblemDisplayID")
                    .invoke(metaTile);
            }
        } catch (Throwable t) {
            // fall through
        }
        return 0;
    }

    public static int getProgressTime(IGregTechTileEntity te) {
        try {
            return getIntField(te, "mProgresstime");
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int getMaxProgressTime(IGregTechTileEntity te) {
        try {
            return getIntField(te, "mMaxProgresstime");
        } catch (Throwable t) {
            return 0;
        }
    }

    public static long getStoredEU(IGregTechTileEntity te) {
        try {
            return te.getStoredEU();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static long getEUCapacity(IGregTechTileEntity te) {
        try {
            return te.getEUCapacity();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static long getInputVoltage(IGregTechTileEntity te) {
        try {
            return te.getInputVoltage();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static long getOutputVoltage(IGregTechTileEntity te) {
        try {
            return te.getOutputVoltage();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static String getRecipeMapName(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                Object recipeMap = metaTile.getClass()
                    .getMethod("getRecipeMap")
                    .invoke(metaTile);
                if (recipeMap != null) {
                    return recipeMap.getClass()
                        .getMethod("getUnlocalizedName")
                        .invoke(recipeMap)
                        .toString();
                }
            }
        } catch (Throwable t) {
            // fall through
        }
        return "Unknown";
    }

    public static String getMachineMode(IGregTechTileEntity te) {
        try {
            // Try getMachineMode() first (MetaTileEntity)
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                Object mode = metaTile.getClass()
                    .getMethod("getMachineMode")
                    .invoke(metaTile);
                if (mode != null) return mode.toString();
            }
        } catch (Throwable t) {
            // fall through
        }
        return "";
    }

    public static int getRepairStatus(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                return getIntField(metaTile, "mRepairStatus");
            }
        } catch (Throwable t) {
            // fall through
        }
        return 0;
    }

    public static int getParallelCount(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                return getIntField(metaTile, "mParallelCount");
            }
        } catch (Throwable t) {
            // fall through
        }
        return 1;
    }

    public static String getCurrentRecipeOutput(IGregTechTileEntity te) {
        try {
            Object metaTile = te.getMetaTileEntity();
            if (metaTile != null) {
                Field field = findFieldInHierarchy(metaTile.getClass(), "mOutputItems");
                if (field != null) {
                    field.setAccessible(true);
                    Object items = field.get(metaTile);
                    if (items != null) {
                        // mOutputItems is usually ItemStack[]
                        Object[] arr = (Object[]) items;
                        if (arr.length > 0 && arr[0] != null) {
                            net.minecraft.item.ItemStack stack = (net.minecraft.item.ItemStack) arr[0];
                            return stack.getDisplayName();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // fall through
        }
        return "";
    }

    // ---- internal reflection helpers ----

    private static int getIntField(Object target, String fieldName) {
        try {
            Field field = findFieldInHierarchy(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.getInt(target);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
